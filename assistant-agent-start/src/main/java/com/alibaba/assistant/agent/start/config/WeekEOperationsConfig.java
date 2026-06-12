package com.alibaba.assistant.agent.start.config;

import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.common.tools.ReplyCodeactTool;
import com.alibaba.assistant.agent.extension.trigger.config.TriggerProperties;
import com.alibaba.assistant.agent.extension.trigger.executor.TriggerExecutor;
import com.alibaba.assistant.agent.extension.trigger.repository.SessionSnapshotRepository;
import com.alibaba.assistant.agent.start.tool.CategoryRankTool;
import com.alibaba.assistant.agent.start.tool.FunnelAnalysisTool;
import com.alibaba.assistant.agent.start.tool.GmvQueryTool;
import com.alibaba.assistant.agent.start.tool.OrderQueryTool;
import com.alibaba.assistant.agent.start.tool.RefundAnalysisTool;
import com.alibaba.assistant.agent.start.tool.RegionPerformanceQueryTool;
import com.alibaba.assistant.agent.start.tool.RootCauseWorkflowTool;
import com.alibaba.assistant.agent.start.tool.UserMetricTool;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableConfigurationProperties({AppReplyProperties.class, AppOperationsProperties.class})
public class WeekEOperationsConfig {

    @Bean
    @Primary
    public TriggerExecutor triggerExecutor(SessionSnapshotRepository snapshotRepository,
                                           TriggerProperties properties,
                                           GmvQueryTool gmvQueryTool,
                                           OrderQueryTool orderQueryTool,
                                           UserMetricTool userMetricTool,
                                           RegionPerformanceQueryTool regionPerformanceQueryTool,
                                           CategoryRankTool categoryRankTool,
                                           FunnelAnalysisTool funnelAnalysisTool,
                                           RefundAnalysisTool refundAnalysisTool,
                                           RootCauseWorkflowTool rootCauseWorkflowTool,
                                           List<ReplyCodeactTool> replyCodeactTools) {
        TriggerProperties.ExecutionConfig config = properties.getExecution();
        List<CodeactTool> tools = new ArrayList<>();
        tools.add(gmvQueryTool);
        tools.add(orderQueryTool);
        tools.add(userMetricTool);
        tools.add(regionPerformanceQueryTool);
        tools.add(categoryRankTool);
        tools.add(funnelAnalysisTool);
        tools.add(refundAnalysisTool);
        tools.add(rootCauseWorkflowTool);
        tools.addAll(replyCodeactTools);
        return new EcommerceTriggerExecutor(
                snapshotRepository,
                config.isAllowIO(),
                config.isAllowNativeAccess(),
                config.getExecutionTimeout(),
                tools
        );
    }
}
