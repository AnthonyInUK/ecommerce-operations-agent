package com.alibaba.assistant.agent.start.audit;

/**
 * 一条 SQL 审计记录：记录 Agent 在一次请求中实际打到数仓的查询。
 *
 * <p>用途：让运营/数据分析师能看到「这个结论背后到底跑了哪条 SQL」，
 * 把 Text-to-Code 从黑盒变成可核验、可追溯的过程。
 *
 * @param sql            归一化后的 SQL（多行折叠成单行，便于阅读）
 * @param params         绑定参数（如日期、区域）；无参数为 null
 * @param rowCount       返回行数；queryForObject 命中为 1、未命中为 0；失败为 -1
 * @param durationMillis 执行耗时（毫秒）
 * @param success        是否执行成功
 * @param timestamp      执行时刻（ISO-8601）
 */
public record SqlAuditEntry(
        String sql,
        String params,
        int rowCount,
        long durationMillis,
        boolean success,
        String timestamp) {
}
