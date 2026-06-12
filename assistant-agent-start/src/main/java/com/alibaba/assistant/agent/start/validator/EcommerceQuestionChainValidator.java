package com.alibaba.assistant.agent.start.validator;

import com.alibaba.assistant.agent.start.config.AppDataSourceProperties;
import com.alibaba.assistant.agent.start.service.EcommerceQuestionAnswerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Order(1010)
public class EcommerceQuestionChainValidator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EcommerceQuestionChainValidator.class);

    private final EcommerceQuestionAnswerService questionAnswerService;
    private final AppDataSourceProperties appDataSourceProperties;

    @Value("${app.validation.strict-startup:true}")
    private boolean strictStartup;

    public EcommerceQuestionChainValidator(EcommerceQuestionAnswerService questionAnswerService,
                                           AppDataSourceProperties appDataSourceProperties) {
        this.questionAnswerService = questionAnswerService;
        this.appDataSourceProperties = appDataSourceProperties;
    }

    @Override
    public void run(String... args) {
        try {
            runValidation();
        }
        catch (RuntimeException ex) {
            if (strictStartup) {
                throw ex;
            }
            log.warn("EcommerceQuestionChainValidator#run - reason=startup validation failed but strictStartup=false, message={}",
                    ex.getMessage(), ex);
        }
    }

    private void runValidation() {
        List<String> questions = List.of(
                "昨天 GMV 多少？",
                "昨天订单量多少，客单价怎么样？",
                "昨天 DAU 和活跃买家多少？",
                "2026-05-17 哪个渠道转化更高？",
                "2026-05-17 华东和华南哪个区域表现更差？",
                "2026-05-17 品类排行怎么看？",
                "2026-05-17 华东 GMV 为什么跌了？"
        );

        for (String question : questions) {
            Map<String, Object> result = questionAnswerService.answer(question);
            validateAnalysisTrace(question, result);
            log.info("EcommerceQuestionChainValidator#run - reason=question answered, question={}, success={}, pathType={}, toolChain={}, answer={}",
                    question,
                    result.get("success"),
                    result.get("path_type"),
                    result.get("tool_chain"),
                    result.get("answer"));
        }

        runMultiTurnScenario("session-gmv", List.of("昨天 GMV 多少？", "那华东呢？"));
        runMultiTurnScenario("session-gmv-category", List.of("昨天 GMV 多少？", "那女装呢？"));
        runMultiTurnScenario("session-order", List.of("昨天订单量多少，客单价怎么样？", "那华东呢？"));
        runMultiTurnScenario("session-user", List.of("昨天 DAU 和活跃买家多少？", "那华东呢？"));
        runMultiTurnScenario("session-refund-followup", List.of("昨天 GMV 多少？", "那退款率呢？"));
        runMultiTurnScenario("session-full-followup", List.of("昨天 GMV 多少？", "那华东呢？", "那女装呢？", "那退款率呢？"));
        runMultiTurnScenario("session-root-cause", List.of("2026-05-17 华东 GMV 为什么跌了？", "那华南呢？"));
        runMultiTurnScenario("session-clarification", List.of("活跃用户怎么样？"));
        runMultiTurnScenario("session-clarification-answer", List.of("活跃用户怎么样？", "DAU"));

        if (appDataSourceProperties.isPreferOlistAnalytics()) {
            testPreferOlistAnalyticsRootCauseLineage();
        }
    }

    private void runMultiTurnScenario(String sessionId, List<String> questions) {
        for (String question : questions) {
            Map<String, Object> result = questionAnswerService.answer(sessionId, question);
            if (Boolean.TRUE.equals(result.get("success"))) {
                validateAnalysisTrace(question, result);
            }
            log.info("EcommerceQuestionChainValidator#runMultiTurnScenario - reason=session question answered, sessionId={}, question={}, success={}, clarification={}, pathType={}, toolChain={}, answerOrMessage={}",
                    sessionId,
                    question,
                    result.get("success"),
                    result.get("requires_clarification"),
                    result.get("path_type"),
                    result.get("tool_chain"),
                    result.getOrDefault("answer", result.get("message")));
        }
    }

    @SuppressWarnings("unchecked")
    private void testPreferOlistAnalyticsRootCauseLineage() {
        String olistRootCauseQuestion = "2018-08-29 华东 GMV 为什么跌了？";
        Map<String, Object> result = questionAnswerService.answer(olistRootCauseQuestion);
        if (!Boolean.TRUE.equals(result.get("success"))) {
            throw new IllegalStateException("prefer-olist-analytics=true 时，root cause 主链执行失败，无法验证 Olist 支线是否生效。");
        }

        validateAnalysisTrace(olistRootCauseQuestion, result);
        validateRootCauseActionRouting(olistRootCauseQuestion, result);

        Map<String, Object> facts = (Map<String, Object>) result.get("facts");
        if (facts == null) {
            throw new IllegalStateException("prefer-olist-analytics=true 时，root cause 结果缺少 facts，无法校验混合数据迁移链。");
        }

        Map<String, Object> dataLineage = (Map<String, Object>) facts.get("data_lineage");
        if (dataLineage == null) {
            throw new IllegalStateException("prefer-olist-analytics=true 时，root cause 结果缺少 data_lineage，无法校验混合数据迁移是否退化。");
        }

        String regionSource = String.valueOf(dataLineage.get("region_metrics_source"));
        String categorySource = String.valueOf(dataLineage.get("category_metrics_source"));
        String orderSource = String.valueOf(dataLineage.get("order_metrics_source"));
        if (!"olist_public_dataset".equals(regionSource)
                || !"olist_public_dataset".equals(categorySource)
                || !"olist_public_dataset".equals(orderSource)) {
            throw new IllegalStateException("prefer-olist-analytics=true 时，root cause 的区域/订单/品类链没有完整走 Olist 支线。当前来源: region="
                    + regionSource + ", order=" + orderSource + ", category=" + categorySource);
        }

        log.info("EcommerceQuestionChainValidator#testPreferOlistAnalyticsRootCauseLineage - reason=olist preferred root cause lineage validated, regionSource={}, orderSource={}, categorySource={}",
                regionSource, orderSource, categorySource);
    }

    @SuppressWarnings("unchecked")
    private void validateRootCauseActionRouting(String question, Map<String, Object> result) {
        Map<String, Object> rootCause = (Map<String, Object>) result.get("root_cause");
        if (rootCause == null) {
            throw new IllegalStateException("问题 " + question + " 缺少 root_cause 结构化结果。");
        }
        List<Map<String, Object>> actionRouting = (List<Map<String, Object>>) rootCause.get("action_routing");
        if (actionRouting == null || actionRouting.isEmpty()) {
            throw new IllegalStateException("问题 " + question + " 缺少 action_routing，无法把原因分发给业务角色。");
        }
        boolean hasCategoryOwner = actionRouting.stream()
                .anyMatch(route -> "category_operation".equals(String.valueOf(route.get("owner_key"))));
        boolean hasAfterSalesOwner = actionRouting.stream()
                .anyMatch(route -> "after_sales_governance".equals(String.valueOf(route.get("owner_key"))));
        if (!hasCategoryOwner || !hasAfterSalesOwner) {
            throw new IllegalStateException("问题 " + question + " 的 action_routing 缺少关键业务负责人。当前分发=" + actionRouting);
        }
        log.info("EcommerceQuestionChainValidator#validateRootCauseActionRouting - reason=root cause action routing validated, routeCount={}",
                actionRouting.size());
    }

    @SuppressWarnings("unchecked")
    private void validateAnalysisTrace(String question, Map<String, Object> result) {
        List<String> toolChain = (List<String>) result.get("tool_chain");
        List<Map<String, Object>> analysisTrace = (List<Map<String, Object>>) result.get("analysis_trace");
        Map<String, Object> traceTags = (Map<String, Object>) result.get("trace_tags");
        if (toolChain == null || toolChain.isEmpty()) {
            throw new IllegalStateException("问题 " + question + " 缺少 tool_chain，无法校验分析轨迹。");
        }
        if (analysisTrace == null || analysisTrace.size() != toolChain.size()) {
            throw new IllegalStateException("问题 " + question + " 的 analysis_trace 与 tool_chain 不一致。toolChain="
                    + toolChain + ", analysisTrace=" + analysisTrace);
        }
        if (traceTags == null || !result.get("path_type").equals(traceTags.get("analysis_path"))) {
            throw new IllegalStateException("问题 " + question + " 的 trace_tags 缺失或 analysis_path 不匹配。");
        }
        log.info("EcommerceQuestionChainValidator#validateAnalysisTrace - reason=analysis trace validated, question={}, analysisDepth={}, toolCount={}",
                question, traceTags.get("analysis_depth"), traceTags.get("tool_count"));
    }
}
