package com.alibaba.assistant.agent.start.service;

import java.util.Optional;
import java.util.UUID;

import com.alibaba.assistant.agent.start.persistence.entity.AnalysisTask;
import com.alibaba.assistant.agent.start.persistence.entity.AnalysisTaskStatus;
import com.alibaba.assistant.agent.start.persistence.repository.AnalysisTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 分析任务的状态机服务：负责落库与每次状态流转，全部走 operational 库的事务。
 *
 * <p>每个状态变更都是一个<b>独立的短事务</b>，真正耗时的分析调用放在事务<i>之外</i>
 * （见 {@link AnalysisAsyncRunner}），避免长时间占着数据库连接。
 */
@Service
public class AnalysisTaskService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisTaskService.class);

    private final AnalysisTaskRepository repository;

    public AnalysisTaskService(AnalysisTaskRepository repository) {
        this.repository = repository;
    }

    /** 受理一个新任务，落库为 SUBMITTED，返回对外任务号。 */
    @Transactional(transactionManager = "operationalTransactionManager")
    public String createTask(String question, String sessionId, String createdBy) {
        String taskId = UUID.randomUUID().toString().replace("-", "");
        AnalysisTask task = new AnalysisTask(taskId, sessionId, question, createdBy);
        repository.save(task);
        log.info("AnalysisTaskService#createTask - reason=task accepted, taskId={}, sessionId={}", taskId, sessionId);
        return taskId;
    }

    @Transactional(transactionManager = "operationalTransactionManager")
    public void markRunning(String taskId) {
        AnalysisTask task = require(taskId);
        task.markRunning();
        repository.save(task);
    }

    @Transactional(transactionManager = "operationalTransactionManager")
    public void markSucceeded(String taskId, String resultText, String toolChain) {
        AnalysisTask task = require(taskId);
        task.markSucceeded(resultText, toolChain);
        repository.save(task);
        log.info("AnalysisTaskService#markSucceeded - reason=task done, taskId={}, elapsedMs={}", taskId, task.getElapsedMs());
    }

    @Transactional(transactionManager = "operationalTransactionManager")
    public void markFailed(String taskId, String errorMessage) {
        AnalysisTask task = require(taskId);
        task.markFailed(errorMessage);
        repository.save(task);
        log.warn("AnalysisTaskService#markFailed - reason=task failed, taskId={}, error={}", taskId, errorMessage);
    }

    @Transactional(transactionManager = "operationalTransactionManager", readOnly = true)
    public Optional<AnalysisTaskView> getView(String taskId) {
        return repository.findByTaskId(taskId).map(AnalysisTaskView::from);
    }

    @Transactional(transactionManager = "operationalTransactionManager", readOnly = true)
    public long countByStatus(AnalysisTaskStatus status) {
        return repository.countByStatus(status);
    }

    private AnalysisTask require(String taskId) {
        return repository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalStateException("analysis task not found: " + taskId));
    }
}
