package com.alibaba.assistant.agent.start.web;

import com.alibaba.assistant.agent.autoconfigure.CodeactAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 开放式问答端点：把问题直接交给框架的 Code-as-Action 智能体（CodeactAgent）。
 *
 * <p>与确定性的 {@code /answer} 快路径不同,这里走真正的大模型驱动路径:
 * 大模型生成 Python 代码 → 在 GraalVM 沙箱中执行 → 返回结果。生成的代码会打到应用日志。
 *
 * <p>需要可用的大模型 key(DashScope 或 OpenAI 兼容的 DeepSeek 等),否则 LLM 调用会被韧性层兜底。
 *
 * <p>{@code POST /api/ecommerce/agent-run}  body: {"question": "..."}
 */
@RestController
@RequestMapping("/api/ecommerce/agent-run")
public class AgentRunController {

    private static final Logger log = LoggerFactory.getLogger(AgentRunController.class);

    private final CodeactAgent codeactAgent;

    public AgentRunController(CodeactAgent codeactAgent) {
        this.codeactAgent = codeactAgent;
    }

    @PostMapping
    public Map<String, Object> run(@RequestBody Map<String, String> body) {
        String question = body.getOrDefault("question", "").trim();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("question", question);
        if (question.isEmpty()) {
            result.put("success", false);
            result.put("message", "question 不能为空");
            return result;
        }
        try {
            long start = System.currentTimeMillis();
            AssistantMessage answer = codeactAgent.call(question);
            result.put("success", true);
            result.put("path_type", "codeact_llm");
            result.put("answer", answer == null ? null : answer.getText());
            result.put("elapsed_ms", System.currentTimeMillis() - start);
            result.put("note", "走大模型驱动的 Code-as-Action;生成的 Python 代码见应用日志(write_code/execute_code)。");
        } catch (Exception e) {
            log.error("AgentRunController#run - reason=Agent 执行失败, question={}", question, e);
            result.put("success", false);
            result.put("message", "Agent 执行失败: " + e.getMessage());
        }
        return result;
    }
}
