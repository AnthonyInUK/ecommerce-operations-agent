package com.alibaba.assistant.agent.extension.experience.internal;

import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceMetadata;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQuery;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQueryContext;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * VectorStoreExperienceProvider 覆盖测试。
 * 用 mock VectorStore 验证：语义召回路径、降级路径、类型/租户过滤、异常降级。
 */
class VectorStoreExperienceProviderTest {

    private VectorStore vectorStore;
    private InMemoryExperienceRepository repo;
    private ExperienceUsageTracker tracker;
    private VectorStoreExperienceProvider provider;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        repo = new InMemoryExperienceRepository();
        tracker = new ExperienceUsageTracker();
        provider = new VectorStoreExperienceProvider(vectorStore, repo, tracker);
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private Experience saveGlobal(ExperienceType type, String name, String content) {
        Experience e = new Experience(type, name, content);
        return repo.save(e);
    }

    private Experience saveTenant(ExperienceType type, String name, String tenantId) {
        Experience e = new Experience(type, name, "内容");
        ExperienceMetadata meta = new ExperienceMetadata();
        meta.addTenantId(tenantId);
        e.setMetadata(meta);
        return repo.save(e);
    }

    private Document docFor(Experience exp) {
        return new Document(exp.getId(), exp.getName(),
                Map.of("experience_id", exp.getId(), "experience_type", exp.getType().name()));
    }

    private ExperienceQuery textQuery(String text) {
        ExperienceQuery q = new ExperienceQuery();
        q.setText(text);
        q.setLimit(10);
        return q;
    }

    // ------------------------------------------------------------------
    // 向量路径
    // ------------------------------------------------------------------

    @Test
    void query_withText_callsVectorStore() {
        Experience exp = saveGlobal(ExperienceType.COMMON, "GMV查询", "查询当日GMV");
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(docFor(exp)));

        List<Experience> result = provider.query(textQuery("今日GMV"), null);

        verify(vectorStore, times(1)).similaritySearch(any(SearchRequest.class));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("GMV查询");
    }

    @Test
    void query_vectorResultFiltersUnknownId_skipped() {
        // 向量返回了一个仓库里不存在的 ID（比如被删掉了）
        Document ghost = new Document("non-existent-id", "幽灵经验",
                Map.of("experience_id", "non-existent-id", "experience_type", "COMMON"));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(ghost));

        List<Experience> result = provider.query(textQuery("什么都没有"), null);

        assertThat(result).isEmpty();
    }

    @Test
    void query_typeFilter_excludesWrongType() {
        Experience common = saveGlobal(ExperienceType.COMMON, "公共经验", "内容");
        Experience tool = saveGlobal(ExperienceType.TOOL, "工具经验", "内容");
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(docFor(common), docFor(tool)));

        ExperienceQuery q = textQuery("查询");
        q.setType(ExperienceType.COMMON);

        List<Experience> result = provider.query(q, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(ExperienceType.COMMON);
    }

    @Test
    void query_tenantFilter_excludesOtherTenantExperiences() {
        Experience global = saveGlobal(ExperienceType.COMMON, "全局经验", "内容");
        Experience tenantA = saveTenant(ExperienceType.COMMON, "租户A经验", "tenantA");
        Experience tenantB = saveTenant(ExperienceType.COMMON, "租户B经验", "tenantB");
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(docFor(global), docFor(tenantA), docFor(tenantB)));

        ExperienceQueryContext ctx = new ExperienceQueryContext();
        ctx.setTenantId("tenantA");

        List<Experience> result = provider.query(textQuery("经验"), ctx);

        // 全局 + tenantA 可见，tenantB 过滤掉
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Experience::getName)
                .containsExactlyInAnyOrder("全局经验", "租户A经验");
    }

    // ------------------------------------------------------------------
    // 降级路径
    // ------------------------------------------------------------------

    @Test
    void query_emptyText_fallsBackToInMemory() {
        saveGlobal(ExperienceType.COMMON, "内存经验", "内容");
        ExperienceQuery q = new ExperienceQuery();
        q.setText("");   // 无文本
        q.setLimit(10);

        provider.query(q, null);

        // VectorStore 不应被调用
        verifyNoInteractions(vectorStore);
    }

    @Test
    void query_fullScanMode_fallsBackToInMemory() {
        saveGlobal(ExperienceType.COMMON, "全量经验", "内容");
        ExperienceQuery q = textQuery("GMV");
        q.setRetrievalMode(ExperienceQuery.RetrievalMode.FULL_SCAN);

        provider.query(q, null);

        verifyNoInteractions(vectorStore);
    }

    @Test
    void query_vectorStoreThrows_fallsBackToInMemory() {
        saveGlobal(ExperienceType.COMMON, "安全经验", "内容");
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new RuntimeException("PGVector连接超时"));

        // 不应抛异常，应降级返回内存结果
        List<Experience> result = provider.query(textQuery("查询"), null);

        assertThat(result).isNotNull();
    }

    // ------------------------------------------------------------------
    // 索引操作
    // ------------------------------------------------------------------

    @Test
    void indexAll_addsAllExperiencesToVectorStore() {
        saveGlobal(ExperienceType.COMMON, "经验1", "内容1");
        saveGlobal(ExperienceType.TOOL, "经验2", "内容2");
        saveGlobal(ExperienceType.REACT, "经验3", "内容3");

        provider.indexAll();

        // 3 条经验一次性 add 到 VectorStore
        verify(vectorStore, times(1)).add(argThat(docs -> docs.size() == 3));
    }

    @Test
    void indexAll_emptyRepo_doesNotCallVectorStore() {
        provider.indexAll();
        verify(vectorStore, never()).add(any());
    }

    @Test
    void index_singleExperience_addedToVectorStore() {
        Experience exp = saveGlobal(ExperienceType.COMMON, "新经验", "内容");
        provider.index(exp);

        verify(vectorStore, times(1)).add(argThat(docs -> docs.size() == 1));
    }
}
