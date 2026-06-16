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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReleaseImpactTool 端到端集成测试：启动 H2 + 真实 schema/seed，
 * 验证「发布前 vs 发布后」窗口划分、订单量/GMV 对比与变化率计算。
 */
class ReleaseImpactToolIntegrationTest {

    private ReleaseImpactTool tool;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:h2:mem:release_" + UUID.randomUUID().toString().replace("-", "")
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

        AppDataSourceProperties props = new AppDataSourceProperties();
        props.setReadCacheEnabled(false);
        JdbcWarehouseQueryService warehouseQueryService =
                new JdbcWarehouseQueryService(new JdbcTemplate(dataSource), props);
        tool = new ReleaseImpactTool(warehouseQueryService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void comparesBeforeAndAfterWindowsWithDelta() throws Exception {
        // 发布日 2026-05-17，窗口 1 天：发布前 = 05-16，发布后 = 05-17
        String input = objectMapper.writeValueAsString(Map.of(
                "release_date", "2026-05-17",
                "window_days", "1"));

        Map<String, Object> result = objectMapper.readValue(tool.call(input), Map.class);

        assertEquals(true, result.get("success"));
        assertEquals("release_impact", result.get("analysis_space"));
        assertEquals("2026-05-16 ~ 2026-05-16", result.get("before_period"));
        assertEquals("2026-05-17 ~ 2026-05-17", result.get("after_period"));

        Map<String, Object> before = (Map<String, Object>) result.get("before");
        Map<String, Object> after = (Map<String, Object>) result.get("after");
        Map<String, Object> delta = (Map<String, Object>) result.get("delta");

        // 05-16 共 3 单 GMV 1510；05-17 共 3 单 GMV 1390（来自 demo seed）
        assertEquals(3, toInt(before.get("order_count")));
        assertEquals(3, toInt(after.get("order_count")));
        assertEquals(1510.0, toDouble(before.get("gmv")), 0.01);
        assertEquals(1390.0, toDouble(after.get("gmv")), 0.01);

        // GMV 变化 = 1390 - 1510 = -120
        assertEquals(-120.0, toDouble(delta.get("gmv_change")), 0.01);
        // 变化率 = -120 / 1510 * 100 ≈ -7.95%
        assertEquals(-7.95, toDouble(delta.get("gmv_change_pct")), 0.1);
        assertEquals(0.0, toDouble(delta.get("order_count_change")), 0.01);
    }

    @Test
    @SuppressWarnings("unchecked")
    void regionFilter_narrowsToThatRegion() throws Exception {
        String input = objectMapper.writeValueAsString(Map.of(
                "release_date", "2026-05-17",
                "window_days", "1",
                "region_name", "华东"));

        Map<String, Object> result = objectMapper.readValue(tool.call(input), Map.class);

        assertEquals(true, result.get("success"));
        assertEquals("华东", result.get("region_name"));
        Map<String, Object> before = (Map<String, Object>) result.get("before");
        // 华东 05-16 是 2 单（o1001/o1002），少于全量的 3 单 → 证明过滤生效
        assertEquals(2, toInt(before.get("order_count")));
    }

    private int toInt(Object v) {
        return ((Number) v).intValue();
    }

    private double toDouble(Object v) {
        return ((Number) v).doubleValue();
    }
}
