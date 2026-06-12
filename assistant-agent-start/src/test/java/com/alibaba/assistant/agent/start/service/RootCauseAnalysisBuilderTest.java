package com.alibaba.assistant.agent.start.service;

import com.alibaba.assistant.agent.start.config.AppOperationsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
