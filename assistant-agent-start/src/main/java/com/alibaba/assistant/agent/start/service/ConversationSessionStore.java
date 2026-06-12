package com.alibaba.assistant.agent.start.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConversationSessionStore {

    private final Map<String, ConversationSessionState> sessions = new ConcurrentHashMap<>();

    public ConversationSessionState get(String sessionId) {
        return sessions.getOrDefault(normalize(sessionId), ConversationSessionState.empty());
    }

    public void save(String sessionId, ConversationSessionState state) {
        sessions.put(normalize(sessionId), state);
    }

    private String normalize(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? "default-session" : sessionId;
    }
}
