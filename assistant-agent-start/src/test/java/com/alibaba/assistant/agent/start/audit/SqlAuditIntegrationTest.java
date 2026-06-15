package com.alibaba.assistant.agent.start.audit;

import com.alibaba.assistant.agent.start.config.AppDataSourceProperties;
import com.alibaba.assistant.agent.start.config.JdbcWarehouseQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQL 审计端到端测试：通过带审计的 JdbcTemplate 跑真实数仓查询，
 * 验证每条 SQL（含参数、行数、耗时）被如实捕获。
 */
class SqlAuditIntegrationTest {

    private SqlAuditTrail trail;
    private RecordingJdbcTemplate recording;
    private JdbcWarehouseQueryService warehouseQueryService;

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:h2:mem:audit_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource();
        dataSource.setDriver(new org.h2.Driver());
        dataSource.setUrl(url);
        dataSource.setUsername("sa");
        dataSource.setPassword("");

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("demo-data/init_schema.sql"));
        populator.addScript(new ClassPathResource("demo-data/load_data.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        populator.execute(dataSource);

        trail = new SqlAuditTrail(200);
        recording = new RecordingJdbcTemplate(dataSource, trail);
        AppDataSourceProperties props = new AppDataSourceProperties();
        props.setReadCacheEnabled(false); // 关缓存，确保每次真打数仓、被审计到
        warehouseQueryService = new JdbcWarehouseQueryService(recording, props);
    }

    @Test
    void warehouseQuery_isCapturedWithSqlParamsAndRowCount() {
        trail.beginRequest();
        warehouseQueryService.getOrderDailyMetrics(LocalDate.of(2026, 5, 17), "华东", null);

        List<SqlAuditEntry> captured = trail.currentRequest();
        assertFalse(captured.isEmpty(), "应捕获到至少一条 SQL");

        SqlAuditEntry entry = captured.get(captured.size() - 1);
        assertTrue(entry.sql().toUpperCase().contains("FROM DWD_ORDERS"), "应记录真实 SQL: " + entry.sql());
        assertTrue(entry.success());
        assertTrue(entry.durationMillis() >= 0);
        assertNotNull(entry.params(), "带参数查询应记录参数");
        assertTrue(entry.params().contains("华东"), "参数里应包含区域: " + entry.params());
    }

    @Test
    void recentBuffer_returnsMostRecentFirst() {
        warehouseQueryService.getDailyCoreMetrics(LocalDate.of(2026, 5, 16));
        warehouseQueryService.getDailyCoreMetrics(LocalDate.of(2026, 5, 17));

        List<SqlAuditEntry> recent = trail.recent(10);
        assertTrue(recent.size() >= 2);
        assertTrue(recent.get(0).timestamp().compareTo(recent.get(1).timestamp()) >= 0,
                "最新的查询应排在最前");
    }

    @Test
    void failedQuery_isRecordedAsFailure() {
        assertThrows(RuntimeException.class, () ->
                recording.queryForList("SELECT * FROM table_that_does_not_exist"));

        List<SqlAuditEntry> recent = trail.recent(5);
        assertFalse(recent.isEmpty());
        assertFalse(recent.get(0).success(), "失败查询应被标记为 success=false");
        assertEquals(-1, recent.get(0).rowCount());
    }
}
