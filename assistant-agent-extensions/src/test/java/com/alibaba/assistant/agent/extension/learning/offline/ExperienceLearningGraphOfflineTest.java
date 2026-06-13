package com.alibaba.assistant.agent.extension.learning.offline;

import com.alibaba.assistant.agent.extension.experience.internal.InMemoryExperienceRepository;
import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.learning.extractor.ExperienceLearningExtractor;
import com.alibaba.assistant.agent.extension.learning.internal.DefaultLearningStrategy;
import com.alibaba.assistant.agent.extension.learning.internal.DefaultLearningExecutor;
import com.alibaba.assistant.agent.extension.learning.internal.AsyncLearningHandler;
import com.alibaba.assistant.agent.extension.learning.internal.JdbcLearningSessionRepository;
import com.alibaba.assistant.agent.extension.learning.model.LearningSessionRecord;
import com.alibaba.assistant.agent.extension.learning.repository.ExperienceLearningRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.jdbc.core.JdbcTemplate;

import com.alibaba.cloud.ai.graph.RunnableConfig;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证离线学习完整链路：
 * 会话日志 → ExperienceLearningGraph → LLM判断/提取 → 写入经验库。
 */
class ExperienceLearningGraphOfflineTest {

    private JdbcLearningSessionRepository sessionRepo;
    private InMemoryExperienceRepository experienceRepo;
    private ExperienceLearningGraph graph;
    private ChatModel mockLlm;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:offline_test_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcTemplate jdbc = new JdbcTemplate(ds);

        sessionRepo = new JdbcLearningSessionRepository(jdbc);
        experienceRepo = new InMemoryExperienceRepository();
        mockLlm = mock(ChatModel.class);

        ExperienceLearningExtractor extractor = new ExperienceLearningExtractor(mockLlm);
        ExperienceLearningRepository learningRepo = new ExperienceLearningRepository(experienceRepo);
        AsyncLearningHandler asyncHandler = new AsyncLearningHandler(1, 10);
        DefaultLearningStrategy strategy = new DefaultLearningStrategy(
                List.of(extractor), List.of(learningRepo), false);
        DefaultLearningExecutor executor = new DefaultLearningExecutor(
                List.of(extractor), List.of(learningRepo), strategy, asyncHandler);

        graph = new ExperienceLearningGraph(executor, List.of(extractor), List.of(learningRepo),
                extractor, experienceRepo, 24, sessionRepo);
    }

    @Test
    void emptySessionLog_graphProducesNoExperiences() {
        graph.buildLearningGraph().stream(null, RunnableConfig.builder().build()).blockLast();
        assertThat(experienceRepo.count()).isZero();
    }

    @Test
    void sessionWithNoContent_isFilteredBeforeLlm() {
        // 工具调用和模型调用都是空数组 → 预处理直接过滤，不调用 LLM
        LearningSessionRecord empty = new LearningSessionRecord();
        empty.setToolCallsJson("[]");
        empty.setModelCallsJson("[]");
        sessionRepo.save(empty);

        graph.buildLearningGraph().stream(null, RunnableConfig.builder().build()).blockLast();

        // LLM 从未被调用
        org.mockito.Mockito.verifyNoInteractions(mockLlm);
        assertThat(experienceRepo.count()).isZero();
    }

    @Test
    void llmSaysYes_experienceIsWrittenToRepo() throws Exception {
        // 准备一条有实质内容的会话记录
        LearningSessionRecord record = new LearningSessionRecord();
        record.setToolCallsJson("[{\"toolName\":\"query_sql\",\"success\":true}]");
        record.setModelCallsJson("[{\"prompt\":\"查询日销售额\",\"response\":\"SELECT...\",\"success\":true}]");
        sessionRepo.save(record);

        // mock LLM：第一次调用（shouldLearn）回答 YES，第二次调用（extract）返回经验 JSON
        ChatResponse yesResponse = mockResponse("YES");
        ChatResponse extractResponse = mockResponse("""
                ```json
                [{"type":"COMMON","title":"日销售额查询","summary":"查询当日GMV的SQL模板","content":"SELECT sum(pay_amount)...","tags":["sql","gmv"]}]
                ```
                """);
        when(mockLlm.call(any(Prompt.class)))
                .thenReturn(yesResponse)
                .thenReturn(extractResponse);

        graph.buildLearningGraph().stream(null, RunnableConfig.builder().build()).blockLast();

        // 验证经验写入了主经验库
        assertThat(experienceRepo.count()).isEqualTo(1);
        List<Experience> experiences = experienceRepo.findAllByType(ExperienceType.COMMON);
        assertThat(experiences).hasSize(1);
        assertThat(experiences.get(0).getName()).isEqualTo("日销售额查询");

        // 验证会话记录已标记为已处理
        List<LearningSessionRecord> remaining = sessionRepo.findUnprocessedSince(
                Instant.now().minus(1, ChronoUnit.HOURS));
        assertThat(remaining).isEmpty();
    }

    @Test
    void llmSaysNo_noExperienceWritten() throws Exception {
        LearningSessionRecord record = new LearningSessionRecord();
        record.setToolCallsJson("[{\"toolName\":\"send_message\",\"success\":true}]");
        record.setModelCallsJson("[]");
        sessionRepo.save(record);

        when(mockLlm.call(any(Prompt.class))).thenReturn(mockResponse("NO"));

        graph.buildLearningGraph().stream(null, RunnableConfig.builder().build()).blockLast();

        assertThat(experienceRepo.count()).isZero();
        // 即使没提取到经验，会话也应被标记为已处理
        List<LearningSessionRecord> remaining = sessionRepo.findUnprocessedSince(
                Instant.now().minus(1, ChronoUnit.HOURS));
        assertThat(remaining).isEmpty();
    }

    private static ChatResponse mockResponse(String text) {
        org.springframework.ai.chat.messages.AssistantMessage msg =
                new org.springframework.ai.chat.messages.AssistantMessage(text);
        Generation generation = new Generation(msg);
        return new ChatResponse(List.of(generation));
    }
}
