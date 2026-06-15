package com.alibaba.assistant.agent.core.resilience;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResilientChatModel 装饰器测试：成功透传、失败降级、无降级文案时抛出。
 */
class ResilientChatModelTest {

    /** 可控行为的假模型：成功返回固定文案，或抛出异常。 */
    static class FakeChatModel implements ChatModel {
        private final Supplier<ChatResponse> behavior;
        FakeChatModel(Supplier<ChatResponse> behavior) {
            this.behavior = behavior;
        }
        @Override
        public ChatResponse call(Prompt prompt) {
            return behavior.get();
        }
        @Override
        public ChatOptions getDefaultOptions() {
            return null;
        }
        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.empty();
        }
    }

    private ChatResponse responseOf(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void success_passesDelegateResponseThrough() {
        ChatModel delegate = new FakeChatModel(() -> responseOf("正常回答"));
        ResilientChatModel model = new ResilientChatModel(delegate,
                ResilientCallExecutor.builder().build(), "系统繁忙，请稍后再试");

        ChatResponse resp = model.call(new Prompt("今日 GMV 多少？"));

        assertEquals("正常回答", resp.getResult().getOutput().getText());
        assertEquals(1, model.getMetrics().getSuccess());
    }

    @Test
    void failure_withFallbackText_returnsDegradedResponse() {
        ChatModel delegate = new FakeChatModel(() -> { throw new RuntimeException("provider 502"); });
        ResilientChatModel model = new ResilientChatModel(delegate,
                ResilientCallExecutor.builder().build(), "系统繁忙，请稍后再试");

        ChatResponse resp = model.call(new Prompt("今日 GMV 多少？"));

        assertEquals("系统繁忙，请稍后再试", resp.getResult().getOutput().getText());
        assertEquals(1, model.getMetrics().getFallback());
        assertEquals(1, model.getMetrics().getFailure());
    }

    @Test
    void failure_withoutFallbackText_propagatesException() {
        ChatModel delegate = new FakeChatModel(() -> { throw new RuntimeException("provider 502"); });
        ResilientChatModel model = new ResilientChatModel(delegate,
                ResilientCallExecutor.builder().build(), null);

        assertThrows(RuntimeException.class, () -> model.call(new Prompt("hi")));
    }
}
