package com.alibaba.assistant.agent.start.config;

import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;



import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@org.springframework.test.context.junit.jupiter.SpringJUnitConfig(RefundCategoryBreakdownCacheTest.TestConfig.class)
public class RefundCategoryBreakdownCacheTest {
    @EnableCaching
    @Configuration
    static class TestConfig {
        @Bean
        JdbcTemplate jdbcTemplate() {
            JdbcTemplate jdbc = mock(JdbcTemplate.class);
            when(jdbc.queryForList(anyString(), any(Object[].class)))
                    .thenReturn(List.of(Map.of("category_l1", "女装", "refund_amount_rate", 0.17)));
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
            return new ConcurrentMapCacheManager("refundCategoryBreakdown");
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
        service.getRefundCategoryBreakdown(date, "华东", 5);
        service.getRefundCategoryBreakdown(date, "华东", 5);
        // 第二次命中缓存：底层 queryForList 只被调用一次。
        verify(jdbcTemplate, times(1)).queryForList(anyString(), any(Object[].class));
    }

    @Test
    void remove_cache() {
        LocalDate date = LocalDate.of(2026, 5, 17);
        service.getRefundCategoryBreakdown(date, "华东", 5);   // miss, 打库第1次
        service.evictRefundCategoryBreakdownCache();            // 清缓存
        service.getRefundCategoryBreakdown(date, "华东", 5);   // 缓存空了, 又打库第2次
        verify(jdbcTemplate, times(2)).queryForList(anyString(), any(Object[].class));
    }

}
