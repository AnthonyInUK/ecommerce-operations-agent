package com.alibaba.assistant.agent.start.persistence.entity;

/**
 * 分析任务的生命周期状态。
 *
 * <p>SUBMITTED(已受理) → RUNNING(分析中) → SUCCEEDED(成功) / FAILED(失败)。
 * 异步任务靠这个状态机驱动"提交即返回、轮询拿结果"的交互。
 */
public enum AnalysisTaskStatus {
    SUBMITTED,
    RUNNING,
    SUCCEEDED,
    FAILED
}
