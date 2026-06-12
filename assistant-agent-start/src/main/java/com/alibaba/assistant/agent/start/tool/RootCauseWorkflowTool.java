package com.alibaba.assistant.agent.start.tool;

import com.alibaba.assistant.agent.common.tools.CodeExample;
import com.alibaba.assistant.agent.start.service.EcommerceQuestionAnswerService;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RootCauseWorkflowTool extends AbstractWarehouseQueryCodeactTool {

    private final EcommerceQuestionAnswerService ecommerceQuestionAnswerService;

    public RootCauseWorkflowTool(EcommerceQuestionAnswerService ecommerceQuestionAnswerService) {
        super(
                "RootCauseWorkflowTool",
                "复用电商 root cause 主链，自动生成原因分析、责任分发、商品商家下钻和通知草稿。",
                List.of(
                        requiredString("stat_date", "统计日期，格式 YYYY-MM-DD"),
                        optionalString("region_name", "区域名，例如华东")
                ),
                "ecommerce_root_cause_workflow",
                "电商经营异常 root cause 工作流",
                new CodeExample(
                        "生成 GMV 异常通知草稿",
                        "result = RootCauseWorkflowTool(stat_date=\"2018-08-29\", region_name=\"华东\")\nprint(result['notification_draft']['title'])",
                        "返回 root cause 结构化结果和可推送通知草稿"
                )
        );
        this.ecommerceQuestionAnswerService = ecommerceQuestionAnswerService;
    }

    @Override
    protected Object execute(Map<String, Object> params, ToolContext toolContext) {
        LocalDate statDate = resolveDate(params);
        String regionName = resolveOptionalString(params, "region_name");
        if (regionName == null) {
            regionName = "华东";
        }

        String question = statDate + " " + regionName + " GMV 为什么跌了？";
        Map<String, Object> answer = ecommerceQuestionAnswerService.answer(
                "trigger-root-cause-" + statDate + "-" + regionName,
                question
        );
        Map<String, Object> rootCause = asMap(answer.get("root_cause"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", Boolean.TRUE.equals(answer.get("success")));
        result.put("analysis_space", "root_cause_workflow");
        result.put("query_type", "trigger_root_cause");
        result.put("stat_date", statDate.toString());
        result.put("region_name", regionName);
        result.put("question", question);
        result.put("answer", answer.get("answer"));
        result.put("root_cause", rootCause);
        result.put("action_routing", rootCause.getOrDefault("action_routing", List.of()));
        result.put("notification_draft", rootCause.getOrDefault("notification_draft", Map.of()));
        result.put("product_seller_drilldown", rootCause.getOrDefault("product_seller_drilldown", List.of()));
        result.put("evidence_confidence", rootCause.getOrDefault("evidence_confidence", Map.of()));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
