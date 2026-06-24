package com.alibaba.assistant.agent.start.persistence;

import com.alibaba.assistant.agent.start.persistence.entity.AnalysisTask;
import com.alibaba.assistant.agent.start.persistence.repository.AnalysisTaskRepository;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 数据层测试专用的最小 Spring 引导配置。
 *
 * <p>{@code @DataJpaTest} 会就近向上找 {@code @SpringBootConfiguration}。把这个放在 persistence
 * 测试包里，让它被选中，从而<b>只装配 JPA 切片</b>（实体 + 仓库 + 测试数据源），
 * 避免误扫到真实 {@code AssistantAgentApplication} 把多数据源/缓存/安全等全套配置拉进来。
 */
@SpringBootConfiguration
@EntityScan(basePackageClasses = AnalysisTask.class)
@EnableJpaRepositories(basePackageClasses = AnalysisTaskRepository.class)
class PersistenceTestApp {
}
