package com.alibaba.assistant.agent.start.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SQL 审计查询端点：给运营/数据分析师看 Agent 最近实际执行了哪些数仓查询。
 *
 * <p>{@code GET /api/ecommerce/sql-audit/recent?limit=20}
 */
@RestController
@RequestMapping("/api/ecommerce/sql-audit")
public class SqlAuditController {

    private final SqlAuditTrail trail;

    public SqlAuditController(SqlAuditTrail trail) {
        this.trail = trail;
    }

    @GetMapping("/recent")
    public Map<String, Object> recent(@RequestParam(defaultValue = "20") int limit) {
        List<SqlAuditEntry> entries = trail.recent(limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count", entries.size());
        body.put("total_recorded", trail.recentCount());
        body.put("entries", entries);
        return body;
    }
}
