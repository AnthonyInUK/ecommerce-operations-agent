package com.alibaba.assistant.agent.start.validator;

import com.alibaba.assistant.agent.start.config.AppDataSourceProperties;
import com.alibaba.assistant.agent.start.config.OlistRawImportAuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(980)
public class OlistRawImportValidator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OlistRawImportValidator.class);

    private final AppDataSourceProperties appDataSourceProperties;
    private final OlistRawImportAuditService olistRawImportAuditService;

    public OlistRawImportValidator(AppDataSourceProperties appDataSourceProperties,
                                   OlistRawImportAuditService olistRawImportAuditService) {
        this.appDataSourceProperties = appDataSourceProperties;
        this.olistRawImportAuditService = olistRawImportAuditService;
    }

    @Override
    public void run(String... args) {
        boolean importRequested = appDataSourceProperties.getOlistRawImportDir() != null
                && !appDataSourceProperties.getOlistRawImportDir().isBlank();

        if (!importRequested && !olistRawImportAuditService.hasRawOrders()) {
            return;
        }

        Map<String, Integer> rowCounts = olistRawImportAuditService.buildRowCountSummary();
        Map<String, Integer> relationshipGaps = olistRawImportAuditService.buildRelationshipGapSummary();

        log.info("OlistRawImportValidator#run - reason=raw olist import summary generated, rowCounts={}, relationshipGaps={}",
                rowCounts, relationshipGaps);

        validateMinimumCoverage(rowCounts);
        validateRelationshipIntegrity(relationshipGaps);
    }

    private void validateMinimumCoverage(Map<String, Integer> rowCounts) {
        requirePositive(rowCounts, "raw_olist_customers");
        requirePositive(rowCounts, "raw_olist_products");
        requirePositive(rowCounts, "raw_olist_orders");
        requirePositive(rowCounts, "raw_olist_order_items");
        requirePositive(rowCounts, "raw_olist_payments");
    }

    private void validateRelationshipIntegrity(Map<String, Integer> relationshipGaps) {
        requireZero(relationshipGaps, "orders_without_customer");
        requireZero(relationshipGaps, "items_without_order");
        requireZero(relationshipGaps, "items_without_product");
        requireZero(relationshipGaps, "payments_without_order");
    }

    private void requirePositive(Map<String, Integer> rowCounts, String key) {
        Integer value = rowCounts.get(key);
        if (value == null || value <= 0) {
            throw new IllegalStateException("Olist raw import coverage check failed: " + key + " has no imported rows.");
        }
    }

    private void requireZero(Map<String, Integer> relationshipGaps, String key) {
        Integer value = relationshipGaps.get(key);
        if (value != null && value > 0) {
            throw new IllegalStateException("Olist raw import integrity check failed: " + key + "=" + value);
        }
    }
}
