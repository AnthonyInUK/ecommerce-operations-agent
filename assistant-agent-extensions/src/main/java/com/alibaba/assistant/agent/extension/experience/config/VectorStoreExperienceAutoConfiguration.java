package com.alibaba.assistant.agent.extension.experience.config;

import com.alibaba.assistant.agent.extension.experience.internal.ExperienceUsageTracker;
import com.alibaba.assistant.agent.extension.experience.internal.InMemoryExperienceRepository;
import com.alibaba.assistant.agent.extension.experience.internal.VectorStoreExperienceProvider;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 当 {@link EmbeddingModel} 可用时，自动启用向量语义检索能力。
 *
 * <p>不可用时（未配置 embedding 模型）静默跳过，系统继续用 n-gram 的
 * {@link com.alibaba.assistant.agent.extension.experience.internal.InMemoryExperienceProvider}。
 *
 * <p>注册两个关键 Bean：
 * <ul>
 *   <li>{@link ExperienceUsageTracker}：全局单例，A（动态置信度）的核心。
 *   <li>{@link VectorStoreExperienceProvider}：替换默认的内存检索，实现 B（语义召回）。
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(ExperienceExtensionAutoConfiguration.class)
@ConditionalOnProperty(prefix = "spring.ai.alibaba.codeact.extension.experience",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@ConditionalOnBean(EmbeddingModel.class)
@Import(VectorStoreExperienceAutoConfiguration.PgVectorStoreConfiguration.class)
public class VectorStoreExperienceAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreExperienceAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(ExperienceUsageTracker.class)
    public ExperienceUsageTracker experienceUsageTracker() {
        log.info("VectorStoreExperienceAutoConfiguration#experienceUsageTracker - reason=创建动态置信度追踪器");
        return new ExperienceUsageTracker();
    }

    /**
     * 在 EmbeddingModel 存在但没有外部 VectorStore 时，用 SimpleVectorStore（纯内存）兜底。
     * 已有 VectorStore Bean 时此 Bean 不会创建（ConditionalOnMissingBean）。
     */
    @Bean
    @ConditionalOnMissingBean(VectorStore.class)
    public VectorStore simpleVectorStore(EmbeddingModel embeddingModel) {
        log.info("VectorStoreExperienceAutoConfiguration#simpleVectorStore - reason=未检测到外部 VectorStore，使用 SimpleVectorStore");
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /**
     * 用 VectorStoreExperienceProvider 替换默认的 InMemoryExperienceProvider。
     *
     * <p>@Primary 确保当两个 ExperienceProvider Bean 同时存在时优先选这个。
     * ExperienceExtensionAutoConfiguration 的 {@code experienceProvider} Bean 上有
     * {@code @ConditionalOnMissingBean}，所以实际上只会有这一个生效。
     * 保留 @Primary 是额外的保障。
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(VectorStoreExperienceProvider.class)
    public ExperienceProvider vectorStoreExperienceProvider(VectorStore vectorStore,
                                                            ExperienceRepository experienceRepository,
                                                            ExperienceUsageTracker usageTracker) {
        VectorStoreExperienceProvider provider =
                new VectorStoreExperienceProvider(vectorStore, experienceRepository, usageTracker);

        // 注册增量索引监听，运行时新增经验自动入向量库
        if (experienceRepository instanceof InMemoryExperienceRepository inMemoryRepo) {
            inMemoryRepo.addSaveListener(provider::index);
            log.info("VectorStoreExperienceAutoConfiguration#vectorStoreExperienceProvider - reason=已注册增量索引监听");
        }

        // 异步全量索引：已有数据（如 PgVectorStore 重启后）直接跳过，避免重复调 embedding API
        Thread indexThread = new Thread(() -> {
            if (provider.isIndexed()) {
                log.info("VectorStoreExperienceAutoConfiguration - reason=向量库已有数据，跳过全量索引");
            } else {
                provider.indexAll();
            }
        }, "experience-index-init");
        indexThread.setDaemon(true);
        indexThread.start();
        log.info("VectorStoreExperienceAutoConfiguration#vectorStoreExperienceProvider - reason=VectorStoreExperienceProvider 创建完成，全量索引异步进行中");

        return provider;
    }

    /**
     * PgVectorStore 配置：仅当 classpath 包含 PgVectorStore 且配置了 PostgreSQL 驱动时激活。
     *
     * <p>隔离在独立内嵌类里，确保未引入 spring-ai-pgvector-store 依赖时不会触发类加载错误。
     * 满足条件时创建的 PgVectorStore bean 会被外层的 {@code @ConditionalOnMissingBean(VectorStore.class)}
     * 检测到，从而让 simpleVectorStore bean 自动跳过。
     *
     * <p>前提（一次性操作）：{@code CREATE EXTENSION IF NOT EXISTS vector;}
     * Spring AI 首次启动时自动建表 vector_store。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(PgVectorStore.class)
    @ConditionalOnMissingBean(VectorStore.class)
    @ConditionalOnBean(JdbcTemplate.class)
    @ConditionalOnProperty(name = "spring.datasource.driver-class-name",
            havingValue = "org.postgresql.Driver")
    static class PgVectorStoreConfiguration {

        @Bean
        public VectorStore pgVectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
            log.info("VectorStoreExperienceAutoConfiguration.PgVectorStoreConfiguration#pgVectorStore" +
                    " - reason=检测到 PostgreSQL 数据源，使用 PgVectorStore 持久化经验向量索引");
            return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                    .indexType(PgVectorStore.PgIndexType.HNSW)
                    .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                    .initializeSchema(true)
                    .build();
        }
    }
}
