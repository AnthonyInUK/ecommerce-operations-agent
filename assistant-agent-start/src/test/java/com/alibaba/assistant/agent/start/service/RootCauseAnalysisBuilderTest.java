package com.alibaba.assistant.agent.start.service;

import com.alibaba.assistant.agent.start.config.AppOperationsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RootCauseAnalysisBuilderTest {

    private RootCauseAnalysisBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new RootCauseAnalysisBuilder(new AppOperationsProperties());
    }

    // ===== computeConfidenceScore =====

    @Test
    void olist_signal_with_highlights_should_score_high() {
        Map<String, Object> highlights = Map.of("previous_gmv", 1000.0, "current_gmv", 700.0);
        Map<String, Object> score = builder.computeConfidenceScore("olist_public_dataset", "signal", highlights);

        assertEquals(92, score.get("score"));   // 40 + 25 + 20 + 7
        assertEquals("high", score.get("level"));
        assertEquals(40, score.get("source_score"));
        assertEquals(25, score.get("dimension_score"));
        assertEquals(20, score.get("magnitude_score"));
        assertEquals(7,  score.get("baseline_score"));
    }

    @Test
    void demo_stable_with_highlights_should_score_medium() {
        Map<String, Object> highlights = Map.of("previous_dau", 5000.0, "current_dau", 4900.0);
        Map<String, Object> score = builder.computeConfidenceScore("demo_seed", "stable", highlights);

        assertEquals(47, score.get("score"));   // 20 + 15 + 8 + 4
        assertEquals("medium", score.get("level"));
    }

    @Test
    void insufficient_status_should_score_zero() {
        Map<String, Object> score = builder.computeConfidenceScore("olist_public_dataset", "insufficient", Map.of());

        assertEquals(0, score.get("score"));
        assertEquals("low", score.get("level"));
        assertEquals(0, score.get("dimension_score"));
        assertEquals(0, score.get("magnitude_score"));
        assertEquals(0, score.get("baseline_score"));
    }

    @Test
    void unknown_source_signal_no_highlights_should_score_low() {
        Map<String, Object> score = builder.computeConfidenceScore("unknown", "signal", Map.of());

        assertEquals(27, score.get("score"));   // 0 + 0 + 20 + 7
        assertEquals("low", score.get("level"));
    }

    // ===== ownerContactFor =====

    @Test
    void platform_operation_contact_should_have_all_fields() {
        Map<String, String> contact = builder.ownerContactFor("platform_operation");

        assertNotNull(contact.get("contact_name"));
        assertNotNull(contact.get("group"));
        assertTrue(contact.get("feishu_webhook").startsWith("mock://feishu/"));
        assertTrue(contact.get("mention").startsWith("@"));
    }

    @Test
    void all_owner_keys_should_return_non_empty_contact() {
        for (String key : new String[]{
                "platform_operation", "category_operation", "growth_operation",
                "business_analysis", "conversion_operation", "after_sales_governance"}) {
            Map<String, String> contact = builder.ownerContactFor(key);
            assertFalse(contact.isEmpty(), "No contact for ownerKey: " + key);
            assertNotNull(contact.get("contact_name"), "Missing contact_name for: " + key);
        }
    }

    @Test
    void unknown_owner_key_should_return_default_contact() {
        Map<String, String> contact = builder.ownerContactFor("unknown_role");
        assertNotNull(contact.get("contact_name"));
        assertTrue(contact.get("feishu_webhook").startsWith("mock://feishu/"));
    }

    // ===== buildCategorySection =====

    @Test
    void category_with_offset_should_report_top_contributor_and_offset() {
        // 家电 -80万，服装 -40万，数码 +50万，总跌幅 -120万（下跌品类之和）
        List<Map<String, Object>> previous = List.of(
            Map.of("CATEGORY_L1", "家电", "GMV", 500.0),
            Map.of("CATEGORY_L1", "服装", "GMV", 300.0),
            Map.of("CATEGORY_L1", "数码", "GMV", 100.0)
        );
        List<Map<String, Object>> current = List.of(
            Map.of("CATEGORY_L1", "家电", "GMV", 420.0),
            Map.of("CATEGORY_L1", "服装", "GMV", 260.0),
            Map.of("CATEGORY_L1", "数码", "GMV", 150.0)
        );

        RootCauseAnalysisResult.Section section = builder.buildCategorySection(current, previous, "olist_public_dataset");

        assertEquals("signal", section.status());
        assertEquals("家电", section.highlights().get("top_category"));
        // 家电贡献度 = 80 / (80+40) ≈ 0.667
        double rate = ((Number) section.highlights().get("top_contribution_rate")).doubleValue();
        assertTrue(rate > 0.6 && rate < 0.7, "Expected ~66.7%, got: " + rate);
        // 数码涨了50，应有对冲
        double offset = ((Number) section.highlights().get("offset_gmv")).doubleValue();
        assertEquals(50.0, offset, 0.01);
    }

    @Test
    void all_categories_declining_should_sum_to_100_percent() {
        List<Map<String, Object>> previous = List.of(
            Map.of("CATEGORY_L1", "家电", "GMV", 600.0),
            Map.of("CATEGORY_L1", "服装", "GMV", 400.0)
        );
        List<Map<String, Object>> current = List.of(
            Map.of("CATEGORY_L1", "家电", "GMV", 540.0),
            Map.of("CATEGORY_L1", "服装", "GMV", 360.0)
        );

        RootCauseAnalysisResult.Section section = builder.buildCategorySection(current, previous, "demo_seed");

        assertEquals("signal", section.status());
        // 家电跌60，服装跌40，总跌100，家电贡献60%
        double rate = ((Number) section.highlights().get("top_contribution_rate")).doubleValue();
        assertEquals(0.6, rate, 0.01);
        // 没有对冲
        double offset = ((Number) section.highlights().get("offset_gmv")).doubleValue();
        assertEquals(0.0, offset, 0.01);
    }

    @Test
    void full_root_cause_result_should_include_metric_bridge_and_ranked_drivers() {
        RootCauseAnalysisResult result = builder.build(
                "华东",
                LocalDate.parse("2018-08-29"),
                LocalDate.parse("2018-08-28"),
                Map.of("data_source", "olist_public_dataset", "rows", List.of(Map.of("GMV", 780.0))),
                Map.of("data_source", "olist_public_dataset", "rows", List.of(Map.of("GMV", 1000.0))),
                Map.of("data_source", "olist_public_dataset", "rows", List.of(Map.of("ORDER_COUNT", 80.0, "AVG_ORDER_VALUE", 9.75))),
                Map.of("data_source", "olist_public_dataset", "rows", List.of(Map.of("ORDER_COUNT", 100.0, "AVG_ORDER_VALUE", 10.0))),
                Map.of("data_source", "demo_seed", "rows", List.of(Map.of("DAU", 900.0, "ACTIVE_BUYER_COUNT", 80.0))),
                Map.of("data_source", "demo_seed", "rows", List.of(Map.of("DAU", 1000.0, "ACTIVE_BUYER_COUNT", 100.0))),
                Map.of("data_source", "olist_public_dataset", "rows", List.of(
                        Map.of("CATEGORY_L1", "家电", "GMV", 430.0),
                        Map.of("CATEGORY_L1", "服装", "GMV", 350.0)
                )),
                Map.of("data_source", "olist_public_dataset", "rows", List.of(
                        Map.of("CATEGORY_L1", "家电", "GMV", 650.0),
                        Map.of("CATEGORY_L1", "服装", "GMV", 350.0)
                )),
                Map.of("rows", List.of(Map.of("VIEW_TO_PAY_RATE", 0.08, "source_tag", "demo_seed"))),
                Map.of("rows", List.of(Map.of("VIEW_TO_PAY_RATE", 0.10, "source_tag", "demo_seed"))),
                Map.of("rows", List.of(Map.of("CATEGORY_L1", "家电", "REFUND_AMOUNT", 60.0, "REFUND_AMOUNT_RATE", 0.08, "source_tag", "demo_seed"))),
                List.of(Map.of("category_l1", "家电", "product_id", "p1", "seller_id", "s1", "gmv_delta", -120.0)),
                List.of(Map.of(
                        "EVIDENCE_DOMAIN", "inventory_fulfillment",
                        "PRODUCT_NAME", "热水壶",
                        "SELLER_NAME", "商家A",
                        "EVIDENCE_SIGNAL", "库存可售天数下降",
                        "PREVIOUS_METRIC", 12,
                        "CURRENT_METRIC", 2,
                        "IMPACT_AMOUNT", -90.0,
                        "SUGGESTED_OWNER", "类目运营"
                ))
        );

        Map<String, Object> payload = result.toMap();
        assertTrue(payload.containsKey("metric_bridge"));
        assertTrue(payload.containsKey("impact_drivers"));
        assertTrue(payload.containsKey("verification_plan"));

        Map<String, Object> metricBridge = (Map<String, Object>) payload.get("metric_bridge");
        assertEquals("GMV = paid_order_count * average_order_value", metricBridge.get("equation"));
        List<Map<String, Object>> impactDrivers = (List<Map<String, Object>>) payload.get("impact_drivers");
        assertFalse(impactDrivers.isEmpty());
        assertEquals(1, impactDrivers.get(0).get("rank"));

        List<Map<String, Object>> verificationPlan = (List<Map<String, Object>>) payload.get("verification_plan");
        assertFalse(verificationPlan.isEmpty());
        assertNotNull(verificationPlan.get(0).get("question_to_verify"));
    }

    @Test
    void missing_region_rows_should_fallback_to_order_gmv() {
        RootCauseAnalysisResult result = builder.build(
                "华东",
                LocalDate.parse("2018-08-29"),
                LocalDate.parse("2018-08-28"),
                Map.of("data_source", "demo_seed", "rows", List.of()),
                Map.of("data_source", "demo_seed", "rows", List.of()),
                Map.of("data_source", "demo_seed", "rows", List.of(Map.of("ORDER_COUNT", 4.0, "PAID_ORDER_COUNT", 4.0, "GROSS_PAY_AMOUNT", 1870.87, "AVG_ORDER_VALUE", 467.7175, "SOURCE_TAG", "demo_seed"))),
                Map.of("data_source", "demo_seed", "rows", List.of(Map.of("ORDER_COUNT", 5.0, "PAID_ORDER_COUNT", 5.0, "GROSS_PAY_AMOUNT", 4361.3, "AVG_ORDER_VALUE", 872.26, "SOURCE_TAG", "demo_seed"))),
                Map.of("data_source", "demo_seed", "rows", List.of(Map.of("DAU", 8.0, "ACTIVE_BUYER_COUNT", 4.0))),
                Map.of("data_source", "demo_seed", "rows", List.of(Map.of("DAU", 10.0, "ACTIVE_BUYER_COUNT", 5.0))),
                Map.of("data_source", "demo_seed", "rows", List.of(Map.of("CATEGORY_L1", "家电", "GMV", 400.0))),
                Map.of("data_source", "demo_seed", "rows", List.of(Map.of("CATEGORY_L1", "家电", "GMV", 2200.0))),
                Map.of("rows", List.of()),
                Map.of("rows", List.of()),
                Map.of("rows", List.of()),
                List.of(),
                List.of()
        );

        Map<String, Object> payload = result.toMap();
        Map<String, Object> overview = (Map<String, Object>) payload.get("overview");
        assertEquals(4361.3, ((Number) overview.get("previous_gmv")).doubleValue(), 0.01);
        assertEquals(1870.87, ((Number) overview.get("current_gmv")).doubleValue(), 0.01);
        assertFalse(result.summary().contains("null"));

        Map<String, Object> facts = result.facts();
        List<Map<String, Object>> currentRegion = (List<Map<String, Object>>) facts.get("current_region");
        assertEquals("order_structure", currentRegion.get(0).get("region_fallback_source"));
    }
}
