package com.alibaba.assistant.agent.start.config;

import javax.sql.DataSource;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
@Order(95)
@ConditionalOnBean(JdbcTemplate.class)
public class DemoWarehouseDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoWarehouseDataInitializer.class);

    private final AppDataSourceProperties properties;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final OlistRawCsvImporter olistRawCsvImporter;

    public DemoWarehouseDataInitializer(
            AppDataSourceProperties properties,
            DataSource dataSource,
            JdbcTemplate jdbcTemplate,
            OlistRawCsvImporter olistRawCsvImporter
    ) {
        this.properties = properties;
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
        this.olistRawCsvImporter = olistRawCsvImporter;
    }

    @Override
    public void run(String... args) {
        bootstrapNotificationDeliverySchema();
        bootstrapBusinessEvidenceSchema();
        bootstrapOlistRawSchemaIfNeeded();
        importOlistRawCsvIfNeeded();
        bootstrapOlistAnalyticsIfNeeded();

        if (!properties.isBootstrapDemoData()) {
            log.info("DemoWarehouseDataInitializer#run - reason=bootstrap demo data disabled");
            return;
        }

        Integer existingTables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'ADS_DAILY_CORE_METRICS'",
                Integer.class
        );
        if (existingTables != null && existingTables > 0) {
            log.info("DemoWarehouseDataInitializer#run - reason=demo warehouse already initialized");
            return;
        }

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding("UTF-8");
        populator.addScript(new ClassPathResource("demo-data/init_schema.sql"));
        populator.addScript(new ClassPathResource("demo-data/load_data.sql"));
        populator.execute(dataSource);

        Integer coreMetricsRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ads_daily_core_metrics", Integer.class);
        log.info("DemoWarehouseDataInitializer#run - reason=demo warehouse initialized, coreMetricRows={}", coreMetricsRows);
    }

    private void bootstrapNotificationDeliverySchema() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding("UTF-8");
        populator.addScript(new ClassPathResource("demo-data/init_notification_delivery_schema.sql"));
        populator.addScript(new ClassPathResource("demo-data/init_anomaly_workflow_schema.sql"));
        populator.execute(dataSource);
        log.info("DemoWarehouseDataInitializer#bootstrapNotificationDeliverySchema - reason=robustness schema ensured");
    }

    private void bootstrapBusinessEvidenceSchema() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding("UTF-8");
        populator.addScript(new ClassPathResource("demo-data/init_business_evidence_schema.sql"));
        populator.execute(dataSource);
        log.info("DemoWarehouseDataInitializer#bootstrapBusinessEvidenceSchema - reason=business evidence schema ensured");
    }

    private void bootstrapOlistRawSchemaIfNeeded() {
        boolean schemaRequested = properties.isBootstrapOlistRawSchema()
                || (properties.getOlistRawImportDir() != null && !properties.getOlistRawImportDir().isBlank());
        if (!schemaRequested) {
            return;
        }

        Integer existingTables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) = 'RAW_OLIST_ORDERS'",
                Integer.class
        );
        if (existingTables != null && existingTables > 0) {
            log.info("DemoWarehouseDataInitializer#bootstrapOlistRawSchemaIfNeeded - reason=olist raw schema already initialized");
            return;
        }

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding("UTF-8");
        populator.addScript(new ClassPathResource("demo-data/olist/raw/init_raw_olist_schema.sql"));
        populator.execute(dataSource);

        Integer rawTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE UPPER(TABLE_NAME) LIKE 'RAW_OLIST_%'",
                Integer.class
        );
        log.info("DemoWarehouseDataInitializer#bootstrapOlistRawSchemaIfNeeded - reason=olist raw schema initialized, rawTableCount={}", rawTableCount);
    }

    private void importOlistRawCsvIfNeeded() {
        String importDir = properties.getOlistRawImportDir();
        if (importDir == null || importDir.isBlank()) {
            return;
        }

        Integer existingRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM raw_olist_orders", Integer.class);
        if (existingRows != null && existingRows > 0) {
            log.info("DemoWarehouseDataInitializer#importOlistRawCsvIfNeeded - reason=raw olist tables already populated, rawOrderCount={}", existingRows);
            return;
        }

        olistRawCsvImporter.importDirectory(Path.of(importDir));
        Integer importedRows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM raw_olist_orders", Integer.class);
        log.info("DemoWarehouseDataInitializer#importOlistRawCsvIfNeeded - reason=raw olist csv import finished, rawOrderCount={}", importedRows);
    }

    private void bootstrapOlistAnalyticsIfNeeded() {
        boolean analyticsRequested = properties.isBootstrapOlistAnalytics() || properties.isPreferOlistAnalytics();
        if (!analyticsRequested) {
            return;
        }

        Integer rawOrderCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM raw_olist_orders", Integer.class);
        if (rawOrderCount == null || rawOrderCount == 0) {
            log.info("DemoWarehouseDataInitializer#bootstrapOlistAnalyticsIfNeeded - reason=skip analytics bootstrap because raw olist data is empty");
            return;
        }

        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding("UTF-8");
        populator.addScript(new ClassPathResource("demo-data/olist/dwd/init_dwd_olist_schema.sql"));
        populator.addScript(new ClassPathResource("demo-data/olist/dwd/build_dwd_from_raw_olist.sql"));
        populator.addScript(new ClassPathResource("demo-data/olist/dim/init_dim_olist_schema.sql"));
        populator.addScript(new ClassPathResource("demo-data/olist/dim/build_dim_from_dwd_olist.sql"));
        populator.addScript(new ClassPathResource("demo-data/olist/ads/init_ads_olist_schema.sql"));
        populator.addScript(new ClassPathResource("demo-data/olist/ads/build_ads_from_dwd_olist.sql"));
        populator.execute(dataSource);

        Integer adsRowCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ads_olist_daily_core_metrics", Integer.class);
        log.info("DemoWarehouseDataInitializer#bootstrapOlistAnalyticsIfNeeded - reason=olist analytics tables built, adsDailyRows={}", adsRowCount);
    }
}
