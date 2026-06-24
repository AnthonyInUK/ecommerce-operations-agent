package com.alibaba.assistant.agent.start.config;

import javax.sql.DataSource;

import com.alibaba.assistant.agent.start.audit.RecordingJdbcTemplate;
import com.alibaba.assistant.agent.start.audit.SqlAuditTrail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableConfigurationProperties(AppDataSourceProperties.class)
public class AppDataSourceConfig {

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "app.datasource", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DataSource warehouseDataSource(AppDataSourceProperties properties) {
        return DataSourceBuilder.create()
                .driverClassName(properties.getDriverClassName())
                .url(properties.getUrl())
                .username(properties.getUsername())
                .password(properties.getPassword())
                .build();
    }

    @Bean
    public SqlAuditTrail sqlAuditTrail(@Value("${app.sql-audit.capacity:200}") int capacity) {
        return new SqlAuditTrail(capacity);
    }

    @Bean
    @Primary
    @ConditionalOnProperty(prefix = "app.datasource", name = "enabled", havingValue = "true", matchIfMissing = true)
    public JdbcTemplate warehouseJdbcTemplate(DataSource warehouseDataSource, SqlAuditTrail sqlAuditTrail) {
        // 用带审计的 JdbcTemplate，捕获每条实际打到数仓的查询（SQL/参数/行数/耗时）。
        // 标 @Primary：引入 operational(@Primary)数据源后，Spring 会按主数据源自动配置出
        // 一个 JdbcTemplate，这里确保所有按类型注入 JdbcTemplate 的数仓消费者仍拿到数仓这个。
        return new RecordingJdbcTemplate(warehouseDataSource, sqlAuditTrail);
    }
}
