package com.alibaba.assistant.agent.start.config;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * operational 库的 JPA + Flyway 显式配置。
 *
 * <p>为什么不靠 Spring Boot 自动配置：自动配置会把 EntityManagerFactory / Flyway 绑到
 * <b>@Primary</b> 数据源，而本应用的 @Primary 是只读数仓。这里显式把它们绑到
 * {@code operationalDataSource}，从而：
 * <ul>
 *   <li>数仓侧的所有现有注入（DataSource / JdbcTemplate）完全不受影响；</li>
 *   <li>JPA 实体与 Flyway 迁移只作用于 operational 库，职责清晰、互不污染。</li>
 * </ul>
 *
 * <p>提供了自定义的 {@link LocalContainerEntityManagerFactoryBean} 和 {@link Flyway} bean 后，
 * Boot 的 HibernateJpaAutoConfiguration / FlywayAutoConfiguration 会自动退避。
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.alibaba.assistant.agent.start.persistence.repository",
        entityManagerFactoryRef = "operationalEntityManagerFactory",
        transactionManagerRef = "operationalTransactionManager"
)
public class OperationalJpaConfig {

    /**
     * 在 operational 库上跑 Flyway 迁移。EMF 依赖它（@DependsOn）以保证建表先于 JPA 启动。
     */
    @Bean(initMethod = "migrate")
    public Flyway operationalFlyway(@Qualifier("operationalDataSource") DataSource operationalDataSource) {
        return Flyway.configure()
                .dataSource(operationalDataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
    }

    @Bean
    @DependsOn("operationalFlyway")
    public LocalContainerEntityManagerFactoryBean operationalEntityManagerFactory(
            @Qualifier("operationalDataSource") DataSource operationalDataSource) {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(operationalDataSource);
        emf.setPackagesToScan("com.alibaba.assistant.agent.start.persistence.entity");
        emf.setPersistenceUnitName("operational");

        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        emf.setJpaVendorAdapter(adapter);

        Map<String, Object> props = new HashMap<>();
        // schema 由 Flyway 管理，Hibernate 不建表也不改表。
        props.put("hibernate.hbm2ddl.auto", "none");
        props.put("hibernate.format_sql", "false");
        emf.setJpaPropertyMap(props);
        return emf;
    }

    @Bean
    public PlatformTransactionManager operationalTransactionManager(
            @Qualifier("operationalEntityManagerFactory") EntityManagerFactory operationalEntityManagerFactory) {
        return new JpaTransactionManager(operationalEntityManagerFactory);
    }
}
