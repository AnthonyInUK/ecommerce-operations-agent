package com.alibaba.assistant.agent.start.tool;

import com.alibaba.assistant.agent.start.config.AppDataSourceProperties;
import com.alibaba.assistant.agent.start.config.JdbcWarehouseQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AbTestJudgeTool 端到端集成测试。
 * 启动内存 H2，加载真实的 init_schema.sql + load_data.sql，
 * 验证转化率是从「访问/付款行为 + 订单」现场算出来的（而非读预聚合假表），
 * 并验证胜负判断逻辑。
 */
class AbTestJudgeToolIntegrationTest {

    private JdbcWarehouseQueryService warehouseQueryService;
    private AbTestJudgeTool tool;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        // 每个测试一个独立的内存库，避免相互污染
        String url = "jdbc:h2:mem:abtest_" + UUID.randomUUID().toString().replace("-", "")
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

        JdbcTemplate jdbcTemplate = new JdbcTemplate((DataSource) dataSource);
        AppDataSourceProperties properties = new AppDataSourceProperties();
        properties.setReadCacheEnabled(false); // 测试里关缓存，确保每次走真实查询
        warehouseQueryService = new JdbcWarehouseQueryService(jdbcTemplate, properties);
        tool = new AbTestJudgeTool(warehouseQueryService);
    }

    @Test
    void conversionRate_isComputedLiveFromBehaviorData() {
        List<Map<String, Object>> rows = warehouseQueryService.getAbTestMetrics(
                "新版结算页_v2",
                java.time.LocalDate.of(2018, 8, 28),
                java.time.LocalDate.of(2018, 8, 29));

        assertEquals(2, rows.size(), "应返回 A、B 两组");

        Map<String, Object> a = findGroup(rows, "A");
        Map<String, Object> b = findGroup(rows, "B");

        // A 组：9 人访问，3 人付款 → 转化率 3/9 ≈ 0.3333
        assertEquals(9, toInt(a.get("exposed_users")));
        assertEquals(3, toInt(a.get("converted_users")));
        assertEquals(0.3333, toDouble(a.get("conversion_rate")), 0.001);

        // B 组：9 人访问，6 人付款 → 转化率 6/9 ≈ 0.6667
        assertEquals(9, toInt(b.get("exposed_users")));
        assertEquals(6, toInt(b.get("converted_users")));
        assertEquals(0.6667, toDouble(b.get("conversion_rate")), 0.001);
    }

    @Test
    void gmvAndOrderCount_comeFromRealOrders_noJoinFanout() {
        List<Map<String, Object>> rows = warehouseQueryService.getAbTestMetrics(
                "新版结算页_v2",
                java.time.LocalDate.of(2018, 8, 28),
                java.time.LocalDate.of(2018, 8, 29));

        Map<String, Object> a = findGroup(rows, "A");
        Map<String, Object> b = findGroup(rows, "B");

        // A 组 3 笔订单：1200 + 1000 + 1000 = 3200（若 JOIN 扇出会翻倍，这里能抓出来）
        assertEquals(3, toInt(a.get("order_count")));
        assertEquals(3200.0, toDouble(a.get("gmv")), 0.01);

        // B 组 6 笔订单：641.30 + 400 + 720.87 + 450 + 300 + 520 = 3032.17
        assertEquals(6, toInt(b.get("order_count")));
        assertEquals(3032.17, toDouble(b.get("gmv")), 0.01);
    }

    @Test
    void tool_declaresBAsWinnerOnConversionRate() throws Exception {
        String input = objectMapper.writeValueAsString(Map.of(
                "experiment_name", "新版结算页_v2",
                "start_date", "2018-08-28",
                "end_date", "2018-08-29",
                "metric", "conversion_rate"));

        Map<String, Object> result = objectMapper.readValue(tool.call(input), Map.class);

        assertEquals(true, result.get("success"));
        assertEquals("B", result.get("winner"));
        // B 比 A 高一倍 → lift ≈ 100%
        assertEquals(100.0, toDouble(result.get("lift_pct")), 0.5);
        assertTrue(String.valueOf(result.get("conclusion")).contains("全量上线"));
    }

    @Test
    void unknownExperiment_returnsFailureWithoutCrash() throws Exception {
        String input = objectMapper.writeValueAsString(Map.of(
                "experiment_name", "不存在的实验",
                "start_date", "2018-08-28",
                "end_date", "2018-08-29"));

        Map<String, Object> result = objectMapper.readValue(tool.call(input), Map.class);

        assertEquals(false, result.get("success"));
    }

    private Map<String, Object> findGroup(List<Map<String, Object>> rows, String groupId) {
        return rows.stream()
                .filter(r -> groupId.equalsIgnoreCase(String.valueOf(r.get("group_id"))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到分组 " + groupId));
    }

    private int toInt(Object value) {
        return ((Number) value).intValue();
    }

    private double toDouble(Object value) {
        return ((Number) value).doubleValue();
    }
}
