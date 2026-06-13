package com.alibaba.assistant.agent.extension.learning.internal;

import com.alibaba.assistant.agent.extension.learning.model.LearningSessionRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * 学习会话日志的 JDBC 持久化实现。
 *
 * <p>每次 Agent 对话结束后写入一条记录，离线学习任务按时间窗口批量读取并复盘。
 */
public class JdbcLearningSessionRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcLearningSessionRepository.class);

    private static final String TABLE = "assistant_learning_session";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public JdbcLearningSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        initSchema();
    }

    public void save(LearningSessionRecord record) {
        jdbc.update(
                "INSERT INTO " + TABLE +
                " (id, session_id, tenant_id, conversation_summary, tool_calls_json, model_calls_json, created_at, offline_processed)" +
                " VALUES (?,?,?,?,?,?,?,?)",
                record.getId(),
                record.getSessionId(),
                record.getTenantId(),
                record.getConversationSummary(),
                record.getToolCallsJson(),
                record.getModelCallsJson(),
                Timestamp.from(record.getCreatedAt()),
                record.isOfflineProcessed()
        );
        log.debug("JdbcLearningSessionRepository#save - id={}", record.getId());
    }

    /**
     * 查询指定时间窗口内尚未被离线学习处理过的会话记录。
     */
    public List<LearningSessionRecord> findUnprocessedSince(Instant since) {
        return jdbc.query(
                "SELECT * FROM " + TABLE +
                " WHERE created_at >= ? AND offline_processed = FALSE ORDER BY created_at ASC",
                new SessionRowMapper(),
                Timestamp.from(since));
    }

    /**
     * 将记录标记为已离线处理，避免下次重复消费。
     */
    public void markProcessed(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Timestamp now = Timestamp.from(Instant.now());
        for (String id : ids) {
            jdbc.update(
                    "UPDATE " + TABLE + " SET offline_processed = TRUE, offline_processed_at = ? WHERE id = ?",
                    now, id);
        }
        log.debug("JdbcLearningSessionRepository#markProcessed - count={}", ids.size());
    }

    private void initSchema() {
        try {
            ClassPathResource resource = new ClassPathResource("db/learning-session-schema.sql");
            String ddl = FileCopyUtils.copyToString(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
            for (String stmt : ddl.split(";")) {
                String trimmed = stmt.strip();
                if (!trimmed.isEmpty()) {
                    try {
                        jdbc.execute(trimmed);
                    } catch (Exception e) {
                        log.debug("JdbcLearningSessionRepository#initSchema - stmt skipped: {}", e.getMessage());
                    }
                }
            }
            log.info("JdbcLearningSessionRepository#initSchema - reason=学习会话日志表初始化完成");
        } catch (Exception e) {
            log.warn("JdbcLearningSessionRepository#initSchema - reason=初始化失败, error={}", e.getMessage());
        }
    }

    private static class SessionRowMapper implements RowMapper<LearningSessionRecord> {

        @Override
        public LearningSessionRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
            LearningSessionRecord r = new LearningSessionRecord();
            r.setId(rs.getString("id"));
            r.setSessionId(rs.getString("session_id"));
            r.setTenantId(rs.getString("tenant_id"));
            r.setConversationSummary(rs.getString("conversation_summary"));
            r.setToolCallsJson(rs.getString("tool_calls_json"));
            r.setModelCallsJson(rs.getString("model_calls_json"));
            Timestamp created = rs.getTimestamp("created_at");
            r.setCreatedAt(created != null ? created.toInstant() : Instant.now());
            r.setOfflineProcessed(rs.getBoolean("offline_processed"));
            Timestamp processed = rs.getTimestamp("offline_processed_at");
            r.setOfflineProcessedAt(processed != null ? processed.toInstant() : null);
            return r;
        }
    }
}
