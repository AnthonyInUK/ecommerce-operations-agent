package com.alibaba.assistant.agent.start.audit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SQL 审计轨迹收集器。
 *
 * <p>两层视图：
 * <ul>
 *   <li><b>全局最近列表</b>（有界环形）：供 {@code /sql-audit/recent} 端点给运营/分析师查看最近执行的查询；</li>
 *   <li><b>当前请求列表</b>（ThreadLocal）：把同一次 Agent 请求里跑的多条 SQL 归到一起，
 *       便于回答「这次分析用了哪些查询」。</li>
 * </ul>
 *
 * <p>说明：被命中缓存的查询不会打到数仓，因此不会出现在审计里——这是正确语义
 * （缓存命中 = 本次没执行 SQL），也能侧面反映缓存效果。
 */
public class SqlAuditTrail {

    private final int capacity;
    private final Deque<SqlAuditEntry> recent = new ConcurrentLinkedDeque<>();
    private final AtomicInteger recentSize = new AtomicInteger();
    private final ThreadLocal<List<SqlAuditEntry>> currentRequest = ThreadLocal.withInitial(ArrayList::new);

    public SqlAuditTrail(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public void record(SqlAuditEntry entry) {
        currentRequest.get().add(entry);
        recent.addLast(entry);
        if (recentSize.incrementAndGet() > capacity) {
            recent.pollFirst();
            recentSize.decrementAndGet();
        }
    }

    /** 最近执行的查询，按时间倒序（最新在前），最多 limit 条。 */
    public List<SqlAuditEntry> recent(int limit) {
        int max = limit <= 0 ? 20 : limit;
        List<SqlAuditEntry> out = new ArrayList<>(Math.min(max, recentSize.get()));
        Iterator<SqlAuditEntry> it = recent.descendingIterator();
        while (it.hasNext() && out.size() < max) {
            out.add(it.next());
        }
        return out;
    }

    /** 开始一次新请求的归集（清空当前线程的请求列表）。 */
    public void beginRequest() {
        currentRequest.get().clear();
    }

    /** 当前请求已执行的 SQL 快照（按执行先后顺序）。 */
    public List<SqlAuditEntry> currentRequest() {
        return Collections.unmodifiableList(new ArrayList<>(currentRequest.get()));
    }

    /** 释放当前线程的请求列表，避免线程池复用导致的内存堆积。 */
    public void clearRequest() {
        currentRequest.remove();
    }

    public int recentCount() {
        return recentSize.get();
    }
}
