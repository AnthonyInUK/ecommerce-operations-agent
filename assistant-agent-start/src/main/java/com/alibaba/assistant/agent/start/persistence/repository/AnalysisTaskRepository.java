package com.alibaba.assistant.agent.start.persistence.repository;

import com.alibaba.assistant.agent.start.persistence.entity.AnalysisTask;
import com.alibaba.assistant.agent.start.persistence.entity.AnalysisTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 分析任务的数据访问层。Spring Data JPA 按方法名派生查询，无需手写实现。
 */
public interface AnalysisTaskRepository extends JpaRepository<AnalysisTask, Long> {

    /** 按对外任务号查询（轮询接口用）。 */
    Optional<AnalysisTask> findByTaskId(String taskId);

    /** 某会话下的任务，最新优先。 */
    List<AnalysisTask> findBySessionIdOrderBySubmittedAtDesc(String sessionId);

    /** 按状态统计，便于监控面板看积压。 */
    long countByStatus(AnalysisTaskStatus status);

    /** 找出卡在运行中、开始时间早于阈值的任务（用于超时清理/排障）。 */
    @Query("select t from AnalysisTask t where t.status = :status and t.startedAt < :before")
    List<AnalysisTask> findStuck(@Param("status") AnalysisTaskStatus status,
                                 @Param("before") Instant before);
}
