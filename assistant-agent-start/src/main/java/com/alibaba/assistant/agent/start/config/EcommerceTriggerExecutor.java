package com.alibaba.assistant.agent.start.config;

import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.extension.trigger.executor.TriggerExecutor;
import com.alibaba.assistant.agent.extension.trigger.model.SessionSnapshot;
import com.alibaba.assistant.agent.extension.trigger.repository.SessionSnapshotRepository;

import java.util.List;

public class EcommerceTriggerExecutor extends TriggerExecutor {

    private final List<CodeactTool> availableTools;

    public EcommerceTriggerExecutor(SessionSnapshotRepository snapshotRepository,
                                    boolean allowIO,
                                    boolean allowNativeAccess,
                                    long executionTimeoutMs,
                                    List<CodeactTool> availableTools) {
        super(snapshotRepository, allowIO, allowNativeAccess, executionTimeoutMs);
        this.availableTools = List.copyOf(availableTools);
    }

    @Override
    protected List<CodeactTool> getAvailableTools(SessionSnapshot snapshot) {
        return availableTools;
    }
}
