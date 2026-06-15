package com.alibaba.assistant.agent.start.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 带 SQL 审计的 JdbcTemplate。
 *
 * <p>所有数仓查询都经过这一个 JdbcTemplate Bean，因此在这里覆写查询方法即可
 * <b>零侵入</b>地捕获每一条实际执行的 SQL（含参数、行数、耗时），无需改动各业务方法。
 *
 * <p>同时做两件事：写入 {@link SqlAuditTrail}（供端点查看），并打印日志（供运营 grep）。
 */
public class RecordingJdbcTemplate extends JdbcTemplate {

    private static final Logger log = LoggerFactory.getLogger("SQL_AUDIT");

    private final SqlAuditTrail trail;

    public RecordingJdbcTemplate(DataSource dataSource, SqlAuditTrail trail) {
        super(dataSource);
        this.trail = trail;
    }

    @Override
    public List<Map<String, Object>> queryForList(String sql) {
        long t0 = System.nanoTime();
        try {
            List<Map<String, Object>> result = super.queryForList(sql);
            record(sql, null, result.size(), t0, true);
            return result;
        } catch (RuntimeException e) {
            record(sql, null, -1, t0, false);
            throw e;
        }
    }

    @Override
    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        long t0 = System.nanoTime();
        try {
            List<Map<String, Object>> result = super.queryForList(sql, args);
            record(sql, args, result.size(), t0, true);
            return result;
        } catch (RuntimeException e) {
            record(sql, args, -1, t0, false);
            throw e;
        }
    }

    @Override
    public <T> T queryForObject(String sql, Class<T> requiredType) {
        long t0 = System.nanoTime();
        try {
            T result = super.queryForObject(sql, requiredType);
            record(sql, null, result == null ? 0 : 1, t0, true);
            return result;
        } catch (RuntimeException e) {
            record(sql, null, -1, t0, false);
            throw e;
        }
    }

    @Override
    public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
        long t0 = System.nanoTime();
        try {
            T result = super.queryForObject(sql, requiredType, args);
            record(sql, args, result == null ? 0 : 1, t0, true);
            return result;
        } catch (RuntimeException e) {
            record(sql, args, -1, t0, false);
            throw e;
        }
    }

    private void record(String sql, Object[] args, int rowCount, long startNanos, boolean success) {
        long durationMillis = (System.nanoTime() - startNanos) / 1_000_000L;
        String oneLineSql = sql == null ? "" : sql.replaceAll("\\s+", " ").trim();
        String params = args == null || args.length == 0 ? null : Arrays.toString(args);
        if (trail != null) {
            trail.record(new SqlAuditEntry(oneLineSql, params, rowCount, durationMillis, success,
                    Instant.now().toString()));
        }
        if (success) {
            log.info("rows={} time={}ms params={} | {}", rowCount, durationMillis, params, oneLineSql);
        } else {
            log.warn("FAILED time={}ms params={} | {}", durationMillis, params, oneLineSql);
        }
    }
}
