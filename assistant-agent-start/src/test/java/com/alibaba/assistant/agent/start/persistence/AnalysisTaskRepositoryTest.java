package com.alibaba.assistant.agent.start.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.alibaba.assistant.agent.start.persistence.entity.AnalysisTask;
import com.alibaba.assistant.agent.start.persistence.entity.AnalysisTaskStatus;
import com.alibaba.assistant.agent.start.persistence.repository.AnalysisTaskRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AnalysisTaskRepository 数据层测试，跑在<b>真实 MySQL（TestContainers）</b>上。
 *
 * <p>用真实 MySQL 而非 H2：直接验证 Flyway 迁移脚本 + 实体映射 + 派生查询在生产同款数据库上成立。
 * 打 {@code @Tag("testcontainers")}，无 Docker 的环境可在 CI 里排除。
 *
 * <p>{@code @ServiceConnection} 把容器连接信息自动接到数据源；schema 由真实的 Flyway 迁移建立
 * （ddl-auto=none），所以这条用例也是对 V1 迁移脚本的一次真库验证。
 */
@Testcontainers
@Tag("testcontainers")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration"
})
class AnalysisTaskRepositoryTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    AnalysisTaskRepository repository;

    @Test
    void save_and_findByTaskId_roundTrips() {
        AnalysisTask task = new AnalysisTask("t-100", "sess-A", "查2026-05-17大盘GMV", "tester");
        repository.save(task);

        Optional<AnalysisTask> found = repository.findByTaskId("t-100");
        assertThat(found).isPresent();
        assertThat(found.get().getStatus()).isEqualTo(AnalysisTaskStatus.SUBMITTED);
        assertThat(found.get().getQuestion()).isEqualTo("查2026-05-17大盘GMV");
        assertThat(found.get().getId()).isNotNull(); // 自增主键由 MySQL 生成
    }

    @Test
    void stateTransitions_persist() {
        AnalysisTask task = new AnalysisTask("t-200", "sess-A", "查退款率", "tester");
        task.markRunning();
        task.markSucceeded("退款率 33%", "RefundAnalysisTool");
        repository.save(task);

        AnalysisTask reloaded = repository.findByTaskId("t-200").orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AnalysisTaskStatus.SUCCEEDED);
        assertThat(reloaded.getResultText()).isEqualTo("退款率 33%");
        assertThat(reloaded.getToolChain()).isEqualTo("RefundAnalysisTool");
        assertThat(reloaded.getElapsedMs()).isNotNull();
    }

    @Test
    void countByStatus_and_findBySession() {
        repository.save(new AnalysisTask("t-301", "sess-B", "q1", "u"));
        AnalysisTask failed = new AnalysisTask("t-302", "sess-B", "q2", "u");
        failed.markFailed("warehouse down");
        repository.save(failed);

        assertThat(repository.countByStatus(AnalysisTaskStatus.FAILED)).isEqualTo(1);
        List<AnalysisTask> sessionTasks = repository.findBySessionIdOrderBySubmittedAtDesc("sess-B");
        assertThat(sessionTasks).extracting(AnalysisTask::getTaskId).contains("t-301", "t-302");
    }

    @Test
    void findStuck_returnsLongRunningTasks() {
        AnalysisTask running = new AnalysisTask("t-400", "sess-C", "slow", "u");
        running.markRunning();
        repository.save(running);

        // 阈值设在未来：所有 RUNNING 任务都算"卡住"。
        List<AnalysisTask> stuck = repository.findStuck(AnalysisTaskStatus.RUNNING, Instant.now().plusSeconds(60));
        assertThat(stuck).extracting(AnalysisTask::getTaskId).contains("t-400");
    }
}
