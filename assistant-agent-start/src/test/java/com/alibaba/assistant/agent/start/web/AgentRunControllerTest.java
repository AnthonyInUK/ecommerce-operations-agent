package com.alibaba.assistant.agent.start.web;

import com.alibaba.assistant.agent.autoconfigure.CodeactAgent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AgentRunController 单测（mock CodeactAgent，不依赖真实大模型，可复现）。
 * 验证开放式问答端点对 Agent 调用结果的封装与异常兜底。
 */
class AgentRunControllerTest {

    @Test
    void run_returnsAgentAnswer() throws Exception {
        CodeactAgent agent = mock(CodeactAgent.class);
        when(agent.call(anyString())).thenReturn(new AssistantMessage("1到100偶数之和是 2550"));

        AgentRunController controller = new AgentRunController(agent);
        Map<String, Object> result = controller.run(Map.of("question", "用代码计算1到100所有偶数的和"));

        assertEquals(true, result.get("success"));
        assertEquals("codeact_llm", result.get("path_type"));
        assertEquals("1到100偶数之和是 2550", result.get("answer"));
        assertNotNull(result.get("elapsed_ms"));
    }

    @Test
    void run_emptyQuestion_rejected() {
        AgentRunController controller = new AgentRunController(mock(CodeactAgent.class));
        Map<String, Object> result = controller.run(Map.of("question", "   "));
        assertEquals(false, result.get("success"));
    }

    @Test
    void run_agentThrows_returnsFailureWithoutCrash() throws Exception {
        CodeactAgent agent = mock(CodeactAgent.class);
        when(agent.call(anyString())).thenThrow(new RuntimeException("graph runner error"));

        AgentRunController controller = new AgentRunController(agent);
        Map<String, Object> result = controller.run(Map.of("question", "随便问问"));

        assertEquals(false, result.get("success"));
        assertTrue(String.valueOf(result.get("message")).contains("Agent 执行失败"));
    }
}
