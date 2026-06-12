package com.alibaba.assistant.agent.start.security;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Component
public class PromptInjectionGuard {

    private static final List<String> HIGH_RISK_PATTERNS = List.of(
            "ignore previous",
            "ignore all previous",
            "system prompt",
            "developer message",
            "print prompt",
            "show prompt",
            "dump prompt",
            "bypass",
            "jailbreak",
            "删表",
            "删除表",
            "改库",
            "更新数据库",
            "打印系统提示词",
            "忽略之前",
            "绕过规则",
            "查看所有表",
            "访问所有表"
    );

    public PromptRisk assess(String sessionId, String question) {
        String normalized = question == null ? "" : question.toLowerCase(Locale.ROOT);
        for (String pattern : HIGH_RISK_PATTERNS) {
            if (normalized.contains(pattern.toLowerCase(Locale.ROOT))) {
                return new PromptRisk(true, "prompt_injection_or_privilege_escalation", pattern);
            }
        }
        return new PromptRisk(false, "normal", null);
    }

    public record PromptRisk(boolean blocked, String riskType, String matchedPattern) {
    }
}
