package com.alibaba.assistant.agent.extension.experience.internal;

import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQuery;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQueryContext;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基于 Spring AI VectorStore 的语义经验检索实现。
 *
 * <p>解决 {@link InMemoryExperienceProvider} 只能做字面 n-gram 匹配的问题：
 * 同一意思用不同说法提问（"昨天 GMV 怎么样" vs "昨日交易额表现如何"），
 * 向量检索都能召回同一批经验。
 *
 * <p>索引策略：
 * <ul>
 *   <li>启动时通过 {@link #indexAll()} 把仓库中所有经验嵌入并写入 VectorStore。
 *   <li>运行时通过 {@link InMemoryExperienceRepository#addSaveListener} 监听新增经验，
 *       自动追加索引，无需重启。
 * </ul>
 *
 * <p>文档格式：每条经验转为一个 {@link Document}，ID 为 experience.getId()，
 * content = name + description + content 拼接，metadata 存 type / tenantIds。
 * 检索后通过 ID 回查仓库取完整对象，避免 Document 存储内容爆炸。
 *
 * <p>当 {@code query.getText()} 为空或 {@code retrievalMode == FULL_SCAN} 时，
 * 降级到 {@link InMemoryExperienceProvider} 处理（FastIntent 需要穷举扫描全量）。
 */
public class VectorStoreExperienceProvider implements ExperienceProvider {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreExperienceProvider.class);

    private static final String META_KEY_EXPERIENCE_ID = "experience_id";
    private static final String META_KEY_TYPE = "experience_type";
    private static final int VECTOR_SEARCH_TOPK_MULTIPLIER = 3;

    private final VectorStore vectorStore;
    private final ExperienceRepository experienceRepository;
    private final InMemoryExperienceProvider fallbackProvider;
    private final ExperienceUsageTracker usageTracker;

    public VectorStoreExperienceProvider(VectorStore vectorStore,
                                         ExperienceRepository experienceRepository,
                                         ExperienceUsageTracker usageTracker) {
        this.vectorStore = vectorStore;
        this.experienceRepository = experienceRepository;
        this.usageTracker = usageTracker;
        this.fallbackProvider = new InMemoryExperienceProvider(experienceRepository, usageTracker);
    }

    /**
     * 探针查询：向量库是否已有数据（用于跳过重复全量索引）。
     * PgVectorStore 重启后数据仍在，SimpleVectorStore 重启后为空。
     */
    public boolean isIndexed() {
        try {
            List<Document> probe = vectorStore.similaritySearch(
                    SearchRequest.builder().query("运营").topK(1).build());
            return !probe.isEmpty();
        } catch (Exception e) {
            log.warn("VectorStoreExperienceProvider#isIndexed - reason=探针查询失败，默认重建索引, error={}", e.getMessage());
            return false;
        }
    }

    /**
     * 在应用启动后调用，将仓库中所有已有经验写入向量索引。
     */
    public void indexAll() {
        List<Document> documents = new ArrayList<>();
        for (ExperienceType type : ExperienceType.values()) {
            for (Experience exp : experienceRepository.findAllByType(type)) {
                documents.add(toDocument(exp));
            }
        }
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
            log.info("VectorStoreExperienceProvider#indexAll - reason=全量索引完成, count={}", documents.size());
        }
    }

    /**
     * 增量索引单条经验，供 saveListener 调用。
     */
    public void index(Experience experience) {
        if (experience == null) {
            return;
        }
        vectorStore.add(List.of(toDocument(experience)));
        log.debug("VectorStoreExperienceProvider#index - reason=增量索引, id={}", experience.getId());
    }

    @Override
    public List<Experience> query(ExperienceQuery query, ExperienceQueryContext context) {
        if (query == null) {
            return new ArrayList<>();
        }

        // FULL_SCAN 或无文本：降级到内存穷举（FastIntent 依赖全量扫描）
        if (query.getRetrievalMode() == ExperienceQuery.RetrievalMode.FULL_SCAN
                || !StringUtils.hasText(query.getText())) {
            log.debug("VectorStoreExperienceProvider#query - reason=降级到内存检索, mode={}", query.getRetrievalMode());
            return fallbackProvider.query(query, context);
        }

        int topK = query.getLimit() * VECTOR_SEARCH_TOPK_MULTIPLIER;
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query.getText())
                .topK(topK)
                .build();

        List<Document> docs;
        try {
            docs = vectorStore.similaritySearch(searchRequest);
        } catch (Exception e) {
            log.warn("VectorStoreExperienceProvider#query - reason=向量检索失败，降级到内存, error={}", e.getMessage());
            return fallbackProvider.query(query, context);
        }

        List<Experience> results = new ArrayList<>();
        for (Document doc : docs) {
            String expId = extractExperienceId(doc);
            if (expId == null) {
                continue;
            }
            Optional<Experience> expOpt = experienceRepository.findById(expId);
            if (expOpt.isEmpty()) {
                continue;
            }
            Experience exp = expOpt.get();

            // 类型过滤
            if (query.getType() != null && !query.getType().equals(exp.getType())) {
                continue;
            }
            // 租户过滤
            if (!matchesTenantId(exp, context)) {
                continue;
            }

            results.add(exp);
            if (results.size() >= query.getLimit()) {
                break;
            }
        }

        log.info("VectorStoreExperienceProvider#query - reason=向量检索完成, query='{}', found={}",
                query.getText(), results.size());
        return results;
    }

    private Document toDocument(Experience exp) {
        String content = buildDocumentContent(exp);
        return new Document(exp.getId(), content, Map.of(
                META_KEY_EXPERIENCE_ID, exp.getId(),
                META_KEY_TYPE, exp.getType() != null ? exp.getType().name() : ""
        ));
    }

    private String buildDocumentContent(Experience exp) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(exp.getName())) {
            sb.append(exp.getName()).append("\n");
        }
        if (StringUtils.hasText(exp.getDescription())) {
            sb.append(exp.getDescription()).append("\n");
        }
        if (StringUtils.hasText(exp.getContent())) {
            // content 可能很长，只取前 500 字符用于索引
            String c = exp.getContent();
            sb.append(c, 0, Math.min(c.length(), 500));
        }
        return sb.toString().trim();
    }

    private String extractExperienceId(Document doc) {
        if (doc.getMetadata() == null) {
            return null;
        }
        Object id = doc.getMetadata().get(META_KEY_EXPERIENCE_ID);
        return id != null ? String.valueOf(id) : null;
    }

    private boolean matchesTenantId(Experience exp, ExperienceQueryContext context) {
        if (exp.getMetadata() == null) {
            return true;
        }
        String tenantId = context != null ? context.getTenantId() : null;
        return exp.getMetadata().matchesTenantId(tenantId);
    }
}
