package com.alibaba.assistant.agent.extension.learning.hook;

import com.alibaba.assistant.agent.extension.learning.internal.JdbcLearningSessionRepository;
import com.alibaba.assistant.agent.extension.learning.model.LearningResult;
import com.alibaba.assistant.agent.extension.learning.model.LearningSessionRecord;
import com.alibaba.assistant.agent.extension.learning.spi.LearningExecutor;
import com.alibaba.assistant.agent.extension.learning.spi.LearningStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AfterAgentLearningHook 覆盖测试。
 * 验证：会话日志异步落库、策略短路、无 sessionRepository 时不崩溃、异常吞咽。
 */
class AfterAgentLearningHookTest {

    private LearningExecutor executor;
    private LearningStrategy strategy;
    private JdbcLearningSessionRepository sessionRepo;

    @BeforeEach
    void setUp() {
        executor = mock(LearningExecutor.class);
        strategy = mock(LearningStrategy.class);

        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:hook_test_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        sessionRepo = new JdbcLearningSessionRepository(new JdbcTemplate(ds));

        // 默认：策略允许触发、同步执行
        when(strategy.shouldTriggerLearning(any())).thenReturn(true);
        when(strategy.shouldExecuteAsync(any())).thenReturn(false);
        when(executor.execute(any())).thenReturn(LearningResult.builder().success(true).build());
    }

    @Test
    void noSessionRepository_hookRunsWithoutNpe() throws Exception {
        // sessionRepository = null → persistSessionLogAsync 静默跳过
        AfterAgentLearningHook hook = new AfterAgentLearningHook(executor, strategy, "experience", null);
        OverAllState state = new OverAllState(Map.of("messages", new ArrayList<>()));

        Map<String, Object> result = hook.afterAgent(state, RunnableConfig.builder().build()).get();

        assertThat(result).isNotNull();
        verify(executor, times(1)).execute(any());
    }

    @Test
    void withSessionRepository_sessionLogIsPersistedAsync() throws Exception {
        AfterAgentLearningHook hook = new AfterAgentLearningHook(executor, strategy, "experience", sessionRepo);

        List<Object> messages = new ArrayList<>();
        messages.add("用户：查询今日销售额");
        messages.add("Agent：SELECT sum(pay_amount) FROM orders WHERE date=today()");
        OverAllState state = new OverAllState(Map.of("messages", messages));

        hook.afterAgent(state, RunnableConfig.builder().build()).get();

        // 等待异步落库完成（最多 2 秒）
        long deadline = System.currentTimeMillis() + 2000;
        List<LearningSessionRecord> records = List.of();
        while (System.currentTimeMillis() < deadline) {
            records = sessionRepo.findUnprocessedSince(Instant.now().minus(1, ChronoUnit.HOURS));
            if (!records.isEmpty()) break;
            Thread.sleep(50);
        }

        assertThat(records).hasSize(1);
        LearningSessionRecord saved = records.get(0);
        // 对话摘要应包含消息内容
        assertThat(saved.getConversationSummary()).contains("查询今日销售额");
        // 工具/模型调用 JSON 至少是空数组（state 里没有执行记录）
        assertThat(saved.getToolCallsJson()).isEqualTo("[]");
        assertThat(saved.getModelCallsJson()).isEqualTo("[]");
    }

    @Test
    void strategyDeclinesToLearn_executorNeverCalled() throws Exception {
        when(strategy.shouldTriggerLearning(any())).thenReturn(false);

        AfterAgentLearningHook hook = new AfterAgentLearningHook(executor, strategy, "experience", null);
        OverAllState state = new OverAllState(Map.of());

        hook.afterAgent(state, RunnableConfig.builder().build()).get();

        // 策略拦截 → 不应调用执行器
        verify(executor, never()).execute(any());
        verify(executor, never()).executeAsync(any());
    }

    @Test
    void asyncExecution_submitsThenReturnsImmediately() throws Exception {
        when(strategy.shouldExecuteAsync(any())).thenReturn(true);
        when(executor.executeAsync(any()))
                .thenReturn(CompletableFuture.completedFuture(LearningResult.builder().success(true).build()));

        AfterAgentLearningHook hook = new AfterAgentLearningHook(executor, strategy, "experience", null);
        OverAllState state = new OverAllState(Map.of());

        Map<String, Object> result = hook.afterAgent(state, RunnableConfig.builder().build()).get();

        assertThat(result).isNotNull();
        verify(executor, times(1)).executeAsync(any());
        verify(executor, never()).execute(any());  // 异步模式不调用同步方法
    }

    @Test
    void exceptionInExecutor_hookStillReturnsNormally() throws Exception {
        when(executor.execute(any())).thenThrow(new RuntimeException("模拟执行器崩溃"));

        AfterAgentLearningHook hook = new AfterAgentLearningHook(executor, strategy, "experience", null);
        OverAllState state = new OverAllState(Map.of());

        // 异常必须被吞掉，不能传播给 Agent 主流程
        Map<String, Object> result = hook.afterAgent(state, RunnableConfig.builder().build()).get();
        assertThat(result).isNotNull();
    }

}
