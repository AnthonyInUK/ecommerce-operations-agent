package com.alibaba.assistant.agent.core.executor;

import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.assistant.agent.core.context.CodeContext;
import com.alibaba.assistant.agent.core.context.SessionCodeManager;
import com.alibaba.assistant.agent.core.executor.python.PythonEnvironmentManager;
import com.alibaba.assistant.agent.core.model.ExecutionRecord;
import com.alibaba.assistant.agent.core.model.GeneratedCode;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraalCodeExecutorDay2ExperimentTest {

    @Test
    void generatedFunctionShouldExecuteInsideGraalSandbox() {
        CodeContext codeContext = new CodeContext(Language.PYTHON);
        codeContext.registerFunction(new GeneratedCode(
                "test_add",
                Language.PYTHON,
                "def test_add(a, b):\n    return a + b",
                "Day 2 experiment: simple function execution"
        ));
        GraalCodeExecutor executor = newExecutor(codeContext, 30000);

        ExecutionRecord record = executor.execute("test_add", Map.of("a", 2, "b", 3));

        assertTrue(record.isSuccess());
        assertTrue(record.getResult().contains("5"));
    }

    @Test
    void sessionCodeShouldOverrideGlobalCodeBeforeSandboxExecution() {
        CodeContext codeContext = new CodeContext(Language.PYTHON);
        codeContext.registerFunction(new GeneratedCode(
                "business_metric",
                Language.PYTHON,
                "def business_metric():\n    return 'global-version'",
                "Global function"
        ));

        OverAllState state = new OverAllState();
        SessionCodeManager.registerSessionFunction(state, new GeneratedCode(
                "business_metric",
                Language.PYTHON,
                "def business_metric():\n    return 'session-version'",
                "Session function"
        ));
        ToolContext toolContext = new ToolContext(Map.of(ToolContextConstants.AGENT_STATE_CONTEXT_KEY, state));
        GraalCodeExecutor executor = newExecutor(codeContext, 30000);

        ExecutionRecord record = executor.execute("business_metric", Map.of(), toolContext);

        assertTrue(record.isSuccess());
        assertTrue(record.getResult().contains("session-version"));
    }

    @Test
    void executionResultShouldBeReturnedAsExecutionRecordString() {
        CodeContext codeContext = new CodeContext(Language.PYTHON);
        codeContext.registerFunction(new GeneratedCode(
                "return_structured_result",
                Language.PYTHON,
                """
                        def return_structured_result():
                            return {"gmv": 1390, "regions": ["华东", "华南"], "success": True}
                        """,
                "Return a dict-like business result"
        ));
        GraalCodeExecutor executor = newExecutor(codeContext, 30000);

        ExecutionRecord record = executor.execute("return_structured_result", Map.of());

        assertTrue(record.isSuccess());
        assertTrue(record.getResult().contains("gmv"));
        assertTrue(record.getResult().contains("1390"));
        assertTrue(record.getResult().contains("华东"));
    }

    @Test
    void executionErrorShouldBePackagedIntoExecutionRecordWithoutExecutorRetry() {
        CodeContext codeContext = new CodeContext(Language.PYTHON);
        codeContext.registerFunction(new GeneratedCode(
                "broken_analysis",
                Language.PYTHON,
                "def broken_analysis():\n    return 1 / 0",
                "Intentional failure"
        ));
        GraalCodeExecutor executor = newExecutor(codeContext, 30000);

        ExecutionRecord record = executor.execute("broken_analysis", Map.of());

        assertFalse(record.isSuccess());
        assertNotNull(record.getErrorMessage());
        assertNotNull(record.getStackTrace());
        assertTrue(record.getStackTrace().contains("broken_analysis"));
    }

    @Test
    void executorImplementationShouldStillBePythonFirstRatherThanDynamicJsSwitching() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/alibaba/assistant/agent/core/executor/GraalCodeExecutor.java"
        ));

        assertTrue(source.contains("Context.newBuilder(\"python\")"));
        assertTrue(source.contains("context.eval(\"python\", code)"));
        assertFalse(source.contains("Context.newBuilder(codeContext.getLanguage()"));
    }

    @Test
    void timeoutAndMemoryLimitShouldBeTreatedAsFrameworkBoundaryInCurrentExecutor() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/alibaba/assistant/agent/core/executor/GraalCodeExecutor.java"
        ));

        assertTrue(source.contains("private final long executionTimeoutMs"));
        assertTrue(source.contains("this.executionTimeoutMs = executionTimeoutMs"));
        assertFalse(source.contains("Future<"));
        assertFalse(source.contains("ExecutorService"));
        assertFalse(source.contains(".get(executionTimeoutMs"));
        assertFalse(source.contains("ResourceLimits"));
        assertFalse(source.contains("memoryLimit"));
    }

    private GraalCodeExecutor newExecutor(CodeContext codeContext, long timeoutMs) {
        return new GraalCodeExecutor(
                new PythonEnvironmentManager(),
                codeContext,
                null,
                new OverAllState(),
                false,
                false,
                timeoutMs
        );
    }
}
