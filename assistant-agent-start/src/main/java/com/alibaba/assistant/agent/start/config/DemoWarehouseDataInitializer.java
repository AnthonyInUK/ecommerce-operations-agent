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
            @org.springframework.beans.factory.annotation.Qualifier("warehouseDataSource") DataSource dataSource,
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

        // 二次守卫：持久化文件库下，若上一轮在 ADS 表建好前就插入了 demo 事实数据（半初始化），
        // 仅靠上面的 ADS 表存在性判断会漏判，导致 load_data.sql 重复插入 raw_orders 主键冲突、
        // 启动崩溃并无限重启。这里再判断 raw_orders 是否已有数据，有则直接跳过，保证幂等。
        if (demoFactDataAlreadyPresent()) {
            log.info("DemoWarehouseDataInitializer#run - reason=demo fact data already present (partial init guard), skip reload");
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

    /**
     * demo 事实数据是否已经存在（防半初始化重复插入）。
     * raw_orders 表不存在或为空都视为"未加载"；任何查询异常都当作未加载，让正常流程继续。
     */
    private boolean demoFactDataAlreadyPresent() {
        try {
            Integer tableExists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'RAW_ORDERS'",
                    Integer.class
            );
            if (tableExists == null || tableExists == 0) {
                return false;
            }
            Integer rows = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM raw_orders", Integer.class);
            return rows != null && rows > 0;
        }
        catch (Exception ex) {
            log.warn("DemoWarehouseDataInitializer#demoFactDataAlreadyPresent - reason=check failed, treat as not loaded, error={}", ex.getMessage());
            return false;
        }
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
