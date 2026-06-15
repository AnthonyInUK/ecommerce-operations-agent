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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OrderAbandonmentTool 端到端集成测试。
 * 启动内存 H2，加载真实的 init_schema.sql + load_data.sql，
 * 验证弃单率和支付失败率是从 dwd_orders 的订单状态现场算出来的。
 */
class OrderAbandonmentToolIntegrationTest {

    private JdbcWarehouseQueryService warehouseQueryService;
    private OrderAbandonmentTool tool;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        String url = "jdbc:h2:mem:abandon_" + UUID.randomUUID().toString().replace("-", "")
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
        properties.setReadCacheEnabled(false);
        warehouseQueryService = new JdbcWarehouseQueryService(jdbcTemplate, properties);
        tool = new OrderAbandonmentTool(warehouseQueryService);
    }

    @Test
    void badDay_highAbandonmentAndPaymentFailure() {
        // 8-30：10 单 = 6 成功 + 2 支付失败 + 2 放弃
        // 弃单率 = 4/10 = 0.40；支付失败率 = 2/(6+2) = 0.25
        List<Map<String, Object>> rows = warehouseQueryService.getOrderAbandonmentMetrics(
                LocalDate.of(2018, 8, 30), LocalDate.of(2018, 8, 30), null, null);

        assertEquals(1, rows.size());
        Map<String, Object> day = rows.get(0);
        assertEquals(10, toInt(day.get("total_orders")));
        assertEquals(6, toInt(day.get("paid_orders")));
        assertEquals(2, toInt(day.get("payment_failed_orders")));
        assertEquals(2, toInt(day.get("canceled_orders")));
        assertEquals(0.40, toDouble(day.get("abandonment_rate")), 0.001);
        assertEquals(0.25, toDouble(day.get("payment_failure_rate")), 0.001);
    }

    @Test
    void fixedDay_ratesDropBack() {
        // 8-31：8 单 = 7 成功 + 1 支付失败
        // 弃单率 = 1/8 = 0.125；支付失败率 = 1/8 = 0.125
        List<Map<String, Object>> rows = warehouseQueryService.getOrderAbandonmentMetrics(
                LocalDate.of(2018, 8, 31), LocalDate.of(2018, 8, 31), null, null);

        Map<String, Object> day = rows.get(0);
        assertEquals(8, toInt(day.get("total_orders")));
        assertEquals(0.125, toDouble(day.get("abandonment_rate")), 0.001);
        assertEquals(0.125, toDouble(day.get("payment_failure_rate")), 0.001);
    }

    @Test
    void tool_returnsBothDaysInRange() throws Exception {
        String input = objectMapper.writeValueAsString(Map.of(
                "start_date", "2018-08-30",
                "end_date", "2018-08-31"));

        Map<String, Object> result = objectMapper.readValue(tool.call(input), Map.class);

        assertEquals(true, result.get("success"));
        assertEquals("order_abandonment_metrics", result.get("query_type"));
        assertEquals(2, toInt(result.get("count")));
    }

    @Test
    void regionFilter_isApplied() {
        // 全部新订单都在华东，按华南过滤应为空
        List<Map<String, Object>> rows = warehouseQueryService.getOrderAbandonmentMetrics(
                LocalDate.of(2018, 8, 30), LocalDate.of(2018, 8, 31), "华南", null);
        assertTrue(rows.isEmpty());
    }

    private int toInt(Object value) {
        return ((Number) value).intValue();
    }

    private double toDouble(Object value) {
        return ((Number) value).doubleValue();
    }
}
