package com.alibaba.assistant.agent.extension.learning.internal;

import com.alibaba.assistant.agent.common.constant.CodeactStateKeys;
import com.alibaba.assistant.agent.core.model.ExecutionRecord;
import com.alibaba.assistant.agent.core.model.GeneratedCode;
import com.alibaba.assistant.agent.extension.learning.model.ModelCallRecord;
import com.alibaba.assistant.agent.extension.learning.model.ToolCallRecord;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 从 OverAllState 中提取学习所需的执行数据。
 *
 * <p>state 里存的是 core 模块的 ExecutionRecord / GeneratedCode，
 * learning 模块有自己的 ToolCallRecord / ModelCallRecord 定义，这里做转换。
 */
public class LearningStateExtractor {

    private static final Logger log = LoggerFactory.getLogger(LearningStateExtractor.class);

    private LearningStateExtractor() {
    }

    /**
     * 从 EXECUTION_HISTORY 中提取所有工具调用记录。
     *
     * <p>每条 ExecutionRecord.callTrace 记录了那次代码执行里调用了哪些工具，
     * 按执行顺序展平成一个列表返回。
     */
    public static List<ToolCallRecord> extractToolCallRecords(OverAllState state) {
        List<ToolCallRecord> result = new ArrayList<>();
        if (state == null) {
            return result;
        }
        try {
            List<?> history = state.value(CodeactStateKeys.EXECUTION_HISTORY, List.class).orElse(null);
            if (history == null || history.isEmpty()) {
                return result;
            }
            for (Object item : history) {
                if (!(item instanceof ExecutionRecord record)) {
                    continue;
                }
                List<com.alibaba.assistant.agent.core.model.ToolCallRecord> callTrace = record.getCallTrace();
                if (callTrace == null) {
                    continue;
                }
                for (com.alibaba.assistant.agent.core.model.ToolCallRecord coreRecord : callTrace) {
                    ToolCallRecord learningRecord = new ToolCallRecord();
                    learningRecord.setToolName(coreRecord.getTool());
                    learningRecord.setSuccess(record.isSuccess());
                    result.add(learningRecord);
                }
            }
            log.debug("LearningStateExtractor#extractToolCallRecords - reason=提取完成, count={}", result.size());
        } catch (Exception e) {
            log.warn("LearningStateExtractor#extractToolCallRecords - reason=提取失败, error={}", e.getMessage());
        }
        return result;
    }

    /**
     * 从 SESSION_GENERATED_CODES 中提取模型调用记录。
     *
     * <p>在 CodeAct 模式下，每次模型生成代码就是一次模型调用。
     * GeneratedCode.originalQuery 是触发这次生成的用户问题，
     * GeneratedCode.code 是模型输出的代码内容。
     */
    public static List<ModelCallRecord> extractModelCallRecords(OverAllState state) {
        List<ModelCallRecord> result = new ArrayList<>();
        if (state == null) {
            return result;
        }
        try {
            Map<?, ?> sessionCodes = state.value(CodeactStateKeys.SESSION_GENERATED_CODES, Map.class).orElse(null);
            if (sessionCodes == null || sessionCodes.isEmpty()) {
                return result;
            }
            for (Object value : sessionCodes.values()) {
                if (!(value instanceof GeneratedCode code)) {
                    continue;
                }
                ModelCallRecord record = new ModelCallRecord();
                record.setPrompt(code.getOriginalQuery());
                record.setResponse(code.getCode());
                record.setSuccess(true);
                result.add(record);
            }
            log.debug("LearningStateExtractor#extractModelCallRecords - reason=提取完成, count={}", result.size());
        } catch (Exception e) {
            log.warn("LearningStateExtractor#extractModelCallRecords - reason=提取失败, error={}", e.getMessage());
        }
        return result;
    }
}
