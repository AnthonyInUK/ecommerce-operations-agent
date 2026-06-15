/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.assistant.agent.core.resilience;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.Supplier;

/**
 * 给任意 Spring AI {@link ChatModel} 套上韧性层的装饰器。
 *
 * <p>对外仍是一个标准 ChatModel，业务代码无感知；内部把每次 {@code call(Prompt)} 通过
 * {@link ResilientCallExecutor} 执行，自动获得限流 / 并发隔离 / 熔断 / 重试 / 超时能力。
 *
 * <p>当请求被熔断短路、被限流挡下、或重试后仍失败时：
 * <ul>
 *   <li>若配置了降级文案，返回一个携带该文案的正常 {@link ChatResponse}（业务侧拿到的是“系统繁忙”
 *       这类可读结果，而不是异常崩溃）；</li>
 *   <li>若未配置降级文案，向上抛出 {@link ResilienceException}，由上层决定如何处理。</li>
 * </ul>
 *
 * <p>流式 {@link #stream(Prompt)} 直接委托底层模型：流式响应的韧性（断流续传等）语义更复杂，
 * 这里只对非流式 {@code call} 做保护，并在文档中明确边界，避免给出似是而非的“全都保护了”。
 */
public class ResilientChatModel implements ChatModel {

    private final ChatModel delegate;
    private final ResilientCallExecutor executor;
    private final String fallbackText;

    public ResilientChatModel(ChatModel delegate, ResilientCallExecutor executor, String fallbackText) {
        this.delegate = delegate;
        this.executor = executor;
        this.fallbackText = fallbackText;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        Supplier<ChatResponse> fallback = fallbackText == null ? null : this::degradedResponse;
        return executor.execute(() -> delegate.call(prompt), fallback);
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return delegate.getDefaultOptions();
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        // 边界：流式不走韧性层，直接委托。
        return delegate.stream(prompt);
    }

    private ChatResponse degradedResponse() {
        AssistantMessage message = new AssistantMessage(fallbackText);
        return new ChatResponse(List.of(new Generation(message)));
    }

    public ResilienceMetrics getMetrics() {
        return executor.getMetrics();
    }

    public ChatModel getDelegate() {
        return delegate;
    }
}
