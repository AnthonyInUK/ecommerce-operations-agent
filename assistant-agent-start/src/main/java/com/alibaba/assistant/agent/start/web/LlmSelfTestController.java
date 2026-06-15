package com.alibaba.assistant.agent.start.web;

import com.alibaba.assistant.agent.core.resilience.ResilienceMetrics;
import com.alibaba.assistant.agent.core.resilience.ResilientCallExecutor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 健康探针 / 自检端点。
 *
 * <p>用一个无副作用的探测 prompt，真实地走一遍「韧性层包装的」LLM 调用（与 Agent 完全同一个
 * {@link ResilientCallExecutor} 实例 —— 同一个熔断器、同一份指标）。
 *
 * <p>双重用途：
 * <ul>
 *   <li><b>线上探活</b>：定时戳一下，确认 LLM provider 健康；故障时韧性指标立刻反映，监控先于用户发现。</li>
 *   <li><b>可观测演示</b>：在没有真实 key 的环境里，调用会失败，正好点亮失败/重试/降级曲线，
 *       直观展示韧性层在 provider 故障下的兜底行为。</li>
 * </ul>
 *
 * <p>{@code POST /api/ecommerce/llm-selftest?times=20}
 */
@RestController
@RequestMapping("/api/ecommerce/llm-selftest")
public class LlmSelfTestController {

    private static final String PROBE_PROMPT = "健康探测：请回复 ok";

    private final ChatModel chatModel;
    private final ResilientCallExecutor executor;
    private final ResilienceMetrics metrics;

    public LlmSelfTestController(ChatModel chatModel,
                                @Autowired(required = false) ResilientCallExecutor executor,
                                @Autowired(required = false) ResilienceMetrics metrics) {
        this.chatModel = chatModel;
        this.executor = executor;
        this.metrics = metrics;
    }

    @PostMapping
    public Map<String, Object> selfTest(@RequestParam(defaultValue = "10") int times) {
        int n = Math.max(1, Math.min(200, times));

        Map<String, Object> before = metrics != null ? metrics.snapshot() : Map.of();
        int probedOk = 0;
        int probedDegraded = 0;

        for (int i = 0; i < n; i++) {
            ChatResponse response;
            if (executor != null) {
                // 走共享韧性执行器：失败时返回降级响应（不抛异常）
                response = executor.execute(() -> chatModel.call(new Prompt(PROBE_PROMPT)), this::degraded);
            } else {
                // 韧性层关闭时直接调用（异常自行兜底）
                try {
                    response = chatModel.call(new Prompt(PROBE_PROMPT));
                } catch (Exception e) {
                    response = degraded();
                }
            }
            if (isDegraded(response)) {
                probedDegraded++;
            } else {
                probedOk++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("probe_count", n);
        result.put("ok", probedOk);
        result.put("degraded", probedDegraded);
        result.put("resilience_enabled", executor != null);
        result.put("metrics_before", before);
        result.put("metrics_after", metrics != null ? metrics.snapshot() : Map.of());
        result.put("note", executor != null
                ? "探测已通过共享韧性执行器执行；查看 /actuator/prometheus 的 llm_resilience_* 指标或 Grafana 仪表盘。"
                : "韧性层未启用（app.llm.resilience.enabled=false）。");
        return result;
    }

    private ChatResponse degraded() {
        return new ChatResponse(List.of(new Generation(new AssistantMessage("[selftest] degraded"))));
    }

    private boolean isDegraded(ChatResponse response) {
        return response != null
                && response.getResult() != null
                && response.getResult().getOutput() != null
                && "[selftest] degraded".equals(response.getResult().getOutput().getText());
    }
}
