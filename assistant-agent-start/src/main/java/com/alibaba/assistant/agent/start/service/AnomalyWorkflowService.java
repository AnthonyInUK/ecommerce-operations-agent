package com.alibaba.assistant.agent.start.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@Service
public class AnomalyWorkflowService {

    private final JdbcTemplate jdbcTemplate;

    public AnomalyWorkflowService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getOrCreate(String anomalyId, String defaultStatus, String defaultAssigneeRole) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT * FROM anomaly_workflow WHERE anomaly_id = ?",
                (rs, rowNum) -> toWorkflow(rs),
                anomalyId
        );
        if (!rows.isEmpty()) {
            return withEvents(rows.get(0));
        }

        jdbcTemplate.update("""
                INSERT INTO anomaly_workflow (
                    anomaly_id, process_status, notification_status, confirmed_by, assignee_role,
                    assignee_user, final_reason, close_note, is_false_positive, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                anomalyId,
                defaultStatus == null || defaultStatus.isBlank() ? "待确认" : defaultStatus,
                "未发送",
                "",
                defaultAssigneeRole == null ? "" : defaultAssigneeRole,
                "",
                "",
                "",
                false
        );
        appendEvent(anomalyId, "Agent", "初始化异常处理状态", "", defaultStatus, "异常进入工作台");
        return withEvents(jdbcTemplate.queryForObject(
                "SELECT * FROM anomaly_workflow WHERE anomaly_id = ?",
                (rs, rowNum) -> toWorkflow(rs),
                anomalyId
        ));
    }

    public Map<String, Map<String, Object>> getOrCreateAll(List<Map<String, Object>> anomalies) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> anomaly : anomalies) {
            String anomalyId = String.valueOf(anomaly.get("id"));
            String status = String.valueOf(anomaly.getOrDefault("status", "待确认"));
            String ownerRole = String.valueOf(anomaly.getOrDefault("owner_role", ""));
            result.put(anomalyId, getOrCreate(anomalyId, status, ownerRole));
        }
        return result;
    }

    public Map<String, Object> confirm(String anomalyId, String actor) {
        Map<String, Object> before = getOrCreate(anomalyId, "待确认", "");
        updateWorkflow(anomalyId, "已确认", null, actor, null, null, null, false);
        appendEvent(anomalyId, actor, "确认异常有效，进入派发前复核", String.valueOf(before.get("process_status")), "已确认", "数据分析师确认异常不是普通波动");
        return getOrCreate(anomalyId, "待确认", "");
    }

    public Map<String, Object> dispatch(String anomalyId, String actor, String assigneeRole, String assigneeUser) {
        Map<String, Object> before = getOrCreate(anomalyId, "待确认", assigneeRole);
        updateWorkflow(anomalyId, "已派发", null, null, assigneeRole, assigneeUser, null, false);
        appendEvent(anomalyId, actor, "派发给责任角色，等待业务方接手", String.valueOf(before.get("process_status")), "已派发", "派发给：" + safe(assigneeRole));
        return getOrCreate(anomalyId, "待确认", assigneeRole);
    }

    public Map<String, Object> accept(String anomalyId, String actor, String assigneeRole) {
        Map<String, Object> before = getOrCreate(anomalyId, "待确认", assigneeRole);
        updateWorkflow(anomalyId, "处理中", null, null, assigneeRole, actor, null, false);
        appendEvent(anomalyId, actor, "接手处理", String.valueOf(before.get("process_status")), "处理中", safe(assigneeRole) + "开始处理异常");
        return getOrCreate(anomalyId, "待确认", assigneeRole);
    }

    public Map<String, Object> record(String anomalyId, String actor, String note) {
        Map<String, Object> before = getOrCreate(anomalyId, "处理中", "");
        updateWorkflow(anomalyId, "处理中", null, null, null, null, null, null);
        appendEvent(anomalyId, actor, "补充处理记录", String.valueOf(before.get("process_status")), "处理中", note == null || note.isBlank() ? "已根据建议动作继续下钻排查" : note);
        return getOrCreate(anomalyId, "处理中", "");
    }

    public Map<String, Object> close(String anomalyId, String actor, String finalReason, String closeNote) {
        Map<String, Object> before = getOrCreate(anomalyId, "处理中", "");
        updateWorkflow(anomalyId, "已关闭", null, null, null, null, finalReason, false);
        jdbcTemplate.update("UPDATE anomaly_workflow SET close_note = ?, updated_at = CURRENT_TIMESTAMP WHERE anomaly_id = ?", safe(closeNote), anomalyId);
        appendEvent(anomalyId, actor, "关闭异常并沉淀最终原因", String.valueOf(before.get("process_status")), "已关闭", safe(finalReason));
        return getOrCreate(anomalyId, "处理中", "");
    }

    public Map<String, Object> falsePositive(String anomalyId, String actor, String reason) {
        Map<String, Object> before = getOrCreate(anomalyId, "待确认", "");
        String beforeStatus = String.valueOf(before.get("process_status"));
        if (!"待确认".equals(beforeStatus) && !"已确认".equals(beforeStatus)) {
            appendEvent(
                    anomalyId,
                    actor,
                    "误报标记被拦截",
                    beforeStatus,
                    beforeStatus,
                    "已派发或处理中异常不能直接标记误报，需要走撤回派发或关闭流程"
            );
            return getOrCreate(anomalyId, "待确认", "");
        }
        updateWorkflow(anomalyId, "误报", null, actor, null, null, reason, true);
        jdbcTemplate.update("UPDATE anomaly_workflow SET close_note = ?, updated_at = CURRENT_TIMESTAMP WHERE anomaly_id = ?", "不建议继续推送业务方。", anomalyId);
        appendEvent(anomalyId, actor, "标记为误报，不再派发业务处理", beforeStatus, "误报", safe(reason));
        return getOrCreate(anomalyId, "待确认", "");
    }

    public Map<String, Object> updateNotification(String anomalyId, String actor, String notificationStatus, String note) {
        Map<String, Object> before = getOrCreate(anomalyId, "待确认", "");
        updateWorkflow(anomalyId, null, notificationStatus, null, null, null, null, null);
        appendEvent(anomalyId, actor, "更新飞书通知状态：" + notificationStatus, String.valueOf(before.get("process_status")), String.valueOf(before.get("process_status")), note);
        return getOrCreate(anomalyId, "待确认", "");
    }

    private void updateWorkflow(String anomalyId,
                                String processStatus,
                                String notificationStatus,
                                String confirmedBy,
                                String assigneeRole,
                                String assigneeUser,
                                String finalReason,
                                Boolean falsePositive) {
        if (processStatus != null) {
            jdbcTemplate.update("UPDATE anomaly_workflow SET process_status = ?, updated_at = CURRENT_TIMESTAMP WHERE anomaly_id = ?", processStatus, anomalyId);
        }
        if (notificationStatus != null) {
            jdbcTemplate.update("UPDATE anomaly_workflow SET notification_status = ?, updated_at = CURRENT_TIMESTAMP WHERE anomaly_id = ?", notificationStatus, anomalyId);
        }
        if (confirmedBy != null) {
            jdbcTemplate.update("UPDATE anomaly_workflow SET confirmed_by = ?, updated_at = CURRENT_TIMESTAMP WHERE anomaly_id = ?", confirmedBy, anomalyId);
        }
        if (assigneeRole != null) {
            jdbcTemplate.update("UPDATE anomaly_workflow SET assignee_role = ?, updated_at = CURRENT_TIMESTAMP WHERE anomaly_id = ?", assigneeRole, anomalyId);
        }
        if (assigneeUser != null) {
            jdbcTemplate.update("UPDATE anomaly_workflow SET assignee_user = ?, updated_at = CURRENT_TIMESTAMP WHERE anomaly_id = ?", assigneeUser, anomalyId);
        }
        if (finalReason != null) {
            jdbcTemplate.update("UPDATE anomaly_workflow SET final_reason = ?, updated_at = CURRENT_TIMESTAMP WHERE anomaly_id = ?", finalReason, anomalyId);
        }
        if (falsePositive != null) {
            jdbcTemplate.update("UPDATE anomaly_workflow SET is_false_positive = ?, updated_at = CURRENT_TIMESTAMP WHERE anomaly_id = ?", falsePositive, anomalyId);
        }
    }

    private void appendEvent(String anomalyId, String actor, String action, String fromStatus, String toStatus, String note) {
        jdbcTemplate.update("""
                INSERT INTO anomaly_workflow_events (anomaly_id, actor, action, from_status, to_status, note, created_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """,
                anomalyId,
                actor == null || actor.isBlank() ? "系统" : actor,
                action == null ? "" : action,
                fromStatus == null ? "" : fromStatus,
                toStatus == null ? "" : toStatus,
                note == null ? "" : note
        );
    }

    private Map<String, Object> withEvents(Map<String, Object> workflow) {
        if (workflow == null) {
            return Map.of();
        }
        String anomalyId = String.valueOf(workflow.get("anomaly_id"));
        workflow.put("events", jdbcTemplate.query(
                "SELECT * FROM anomaly_workflow_events WHERE anomaly_id = ? ORDER BY created_at DESC, id DESC LIMIT 20",
                (rs, rowNum) -> {
                    Map<String, Object> event = new LinkedHashMap<>();
                    event.put("id", rs.getLong("id"));
                    event.put("anomaly_id", rs.getString("anomaly_id"));
                    event.put("actor", rs.getString("actor"));
                    event.put("action", rs.getString("action"));
                    event.put("from_status", rs.getString("from_status"));
                    event.put("to_status", rs.getString("to_status"));
                    event.put("note", rs.getString("note"));
                    event.put("created_at", String.valueOf(rs.getTimestamp("created_at")));
                    return event;
                },
                anomalyId
        ));
        return workflow;
    }

    private Map<String, Object> toWorkflow(ResultSet rs) throws SQLException {
        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("anomaly_id", rs.getString("anomaly_id"));
        workflow.put("process_status", rs.getString("process_status"));
        workflow.put("notification_status", rs.getString("notification_status"));
        workflow.put("confirmed_by", rs.getString("confirmed_by"));
        workflow.put("assignee_role", rs.getString("assignee_role"));
        workflow.put("assignee_user", rs.getString("assignee_user"));
        workflow.put("final_reason", rs.getString("final_reason"));
        workflow.put("close_note", rs.getString("close_note"));
        workflow.put("is_false_positive", rs.getBoolean("is_false_positive"));
        workflow.put("created_at", String.valueOf(rs.getTimestamp("created_at")));
        workflow.put("updated_at", String.valueOf(rs.getTimestamp("updated_at")));
        return workflow;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
