package com.alibaba.assistant.agent.start.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * operational（可写）数据源配置。
 *
 * <p>本应用有两套数据源，职责清晰分离：
 * <ul>
 *   <li><b>operational 库（这里，@Primary）</b>：MySQL/H2，存分析任务记录与审计，
 *       由 JPA + Flyway 管理。绑定 {@code spring.datasource.*}。</li>
 *   <li><b>数仓库</b>：见 {@link AppDataSourceConfig#warehouseDataSource}，只读分析数据，
 *       走带审计的 JdbcTemplate。绑定 {@code app.datasource.*}。</li>
 * </ul>
 *
 * <p>数仓数据源是 @Primary（保持所有现有 warehouse 消费者按类型注入不变）；本数据源是
 * 命名次级 bean，只通过 {@code @Qualifier("operationalDataSource")} 被 JPA 显式引用
 * （见 {@link OperationalJpaConfig}）。这里用 {@link DataSourceProperties} 而非裸
 * {@code DataSourceBuilder}，因为前者会把 spring.datasource.url 正确映射到连接池的 jdbcUrl。
 */
@Configuration
public class OperationalDataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties operationalDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource operationalDataSource(
            @Qualifier("operationalDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }
}
