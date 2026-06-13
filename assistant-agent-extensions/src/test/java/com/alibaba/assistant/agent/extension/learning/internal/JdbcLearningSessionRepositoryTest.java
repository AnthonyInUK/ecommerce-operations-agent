package com.alibaba.assistant.agent.extension.learning.internal;

import com.alibaba.assistant.agent.extension.learning.model.LearningSessionRecord;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证学习会话日志的写入、时间窗口查询、已处理标记三条路径。
 * 使用内存 H2，无需 Spring 上下文。
 */
class JdbcLearningSessionRepositoryTest {

    private JdbcLearningSessionRepository repo;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:learning_test_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        repo = new JdbcLearningSessionRepository(new JdbcTemplate(ds));
    }

    @Test
    void saveThenFindUnprocessed() {
        LearningSessionRecord record = sessionRecord("[{\"toolName\":\"query_sql\"}]", "[{\"prompt\":\"日销售额\"}]");
        repo.save(record);

        List<LearningSessionRecord> results = repo.findUnprocessedSince(Instant.now().minus(1, ChronoUnit.HOURS));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(record.getId());
        assertThat(results.get(0).getToolCallsJson()).contains("query_sql");
        assertThat(results.get(0).isOfflineProcessed()).isFalse();
    }

    @Test
    void markProcessedHidesFromNextQuery() {
        LearningSessionRecord r1 = sessionRecord("[{\"toolName\":\"query_sql\"}]", "[]");
        LearningSessionRecord r2 = sessionRecord("[{\"toolName\":\"send_message\"}]", "[]");
        repo.save(r1);
        repo.save(r2);

        repo.markProcessed(List.of(r1.getId()));

        List<LearningSessionRecord> remaining = repo.findUnprocessedSince(Instant.now().minus(1, ChronoUnit.HOURS));
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getId()).isEqualTo(r2.getId());
    }

    @Test
    void recordsOutsideTimeWindowAreExcluded() {
        LearningSessionRecord old = sessionRecord("[{\"toolName\":\"query_sql\"}]", "[]");
        // Backdated to 48 hours ago
        old.setCreatedAt(Instant.now().minus(48, ChronoUnit.HOURS));
        repo.save(old);

        List<LearningSessionRecord> results = repo.findUnprocessedSince(Instant.now().minus(24, ChronoUnit.HOURS));
        assertThat(results).isEmpty();
    }

    private LearningSessionRecord sessionRecord(String toolCallsJson, String modelCallsJson) {
        LearningSessionRecord r = new LearningSessionRecord();
        r.setToolCallsJson(toolCallsJson);
        r.setModelCallsJson(modelCallsJson);
        r.setConversationSummary("用户询问日销售额");
        return r;
    }
}
