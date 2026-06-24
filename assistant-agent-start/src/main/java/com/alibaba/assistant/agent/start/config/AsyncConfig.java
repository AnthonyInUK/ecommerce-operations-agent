package com.alibaba.assistant.agent.start.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步执行配置。
 *
 * <p>运营分析是"长耗时"任务（要查多张表、可能走大模型），同步接口容易超时。这里开一个
 * 专用线程池跑分析任务，让"提交"立即返回任务号，客户端再轮询结果。
 *
 * <p>线程池用<b>有界队列 + AbortPolicy</b>：队列满时直接拒绝而不是无限堆积，
 * 调用方据此把任务标记为失败并提示"服务繁忙"——这是明确的背压，而不是拖垮系统。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String ANALYSIS_EXECUTOR = "analysisTaskExecutor";

    @Bean(name = ANALYSIS_EXECUTOR)
    public Executor analysisTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("analysis-task-");
        // 队列满且线程用尽时直接拒绝（抛 TaskRejectedException），由提交方转成失败状态。
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 优雅停机：等在跑的分析任务跑完再关闭。
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
