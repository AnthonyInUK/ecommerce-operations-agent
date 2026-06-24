package com.alibaba.assistant.agent.start.config;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证 getDailyCoreMetrics 的 @Cacheable 真生效：同一日期查两次只打一次数仓，
 * 不同日期各打一次（按 key 区分）。用真实 Spring 上下文（代理生效）+ mock JdbcTemplate 计数。
 */
@SpringJUnitConfig(DailyCoreMetricsCacheTest.TestConfig.class)
class DailyCoreMetricsCacheTest {

    @EnableCaching
    @Configuration
    static class TestConfig {
        @Bean
        JdbcTemplate jdbcTemplate() {
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            when(jdbc.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of(Map.of("gmv", 1390.0)));
            return jdbc;
        }

        @Bean
        AppDataSourceProperties appDataSourceProperties() {
            AppDataSourceProperties props = new AppDataSourceProperties();
            props.setPreferOlistAnalytics(false); // 走 demo_seed 单条查询，不触发 olist 探测
            return props;
        }

        @Bean
        org.springframework.cache.CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("dailyCoreMetrics");
        }

        @Bean
        JdbcWarehouseQueryService warehouseQueryService(JdbcTemplate jdbcTemplate,
                                                        AppDataSourceProperties props) {
            return new JdbcWarehouseQueryService(jdbcTemplate, props);
        }
    }

    @Autowired
    JdbcWarehouseQueryService service;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void sameDate_queriedTwice_hitsWarehouseOnce() {
        LocalDate date = LocalDate.of(2026, 5, 17);
        service.getDailyCoreMetrics(date);
        service.getDailyCoreMetrics(date);

        // 第二次命中缓存：底层 queryForList 只被调用一次。
        verify(jdbcTemplate, times(1)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    void differentDates_eachQueriesWarehouse() {
        service.getDailyCoreMetrics(LocalDate.of(2026, 5, 17));
        service.getDailyCoreMetrics(LocalDate.of(2026, 5, 18));

        List<Map<String, Object>> r = service.getDailyCoreMetrics(LocalDate.of(2026, 5, 17)); // 再查首日 → 缓存
        assertEquals(1390.0, r.get(0).get("gmv"));
        // 两个不同 key 各打一次，第三次命中缓存：共 2 次。
        verify(jdbcTemplate, times(2)).queryForList(anyString(), any(Object[].class));
    }
}
