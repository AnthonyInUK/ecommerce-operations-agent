package com.alibaba.assistant.agent.extension.experience.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 运行时经验命中计数器。
 *
 * <p>每次 LLM 调用 read_exp 读取某条经验，计一次命中。命中越多，说明这条经验被模型持续采纳，
 * 有效置信度随之上升，在 SCORE 排序中排到更前面。
 *
 * <p>当前为纯内存实现，重启后归零。若需持久化可替换为基于数据库的实现。
 */
public class ExperienceUsageTracker {

    private static final Logger log = LoggerFactory.getLogger(ExperienceUsageTracker.class);

    private static final double HIT_BONUS_PER_COUNT = 0.05;
    private static final double MAX_HIT_BONUS = 0.30;
    private static final double DEFAULT_BASE_CONFIDENCE = 0.5;

    private final ConcurrentHashMap<String, AtomicInteger> hitCounts = new ConcurrentHashMap<>();

    public void recordHit(String experienceId) {
        if (experienceId == null || experienceId.isBlank()) {
            return;
        }
        int newCount = hitCounts
                .computeIfAbsent(experienceId, id -> new AtomicInteger(0))
                .incrementAndGet();
        log.debug("ExperienceUsageTracker#recordHit - id={}, totalHits={}", experienceId, newCount);
    }

    public int getHitCount(String experienceId) {
        if (experienceId == null) {
            return 0;
        }
        AtomicInteger counter = hitCounts.get(experienceId);
        return counter != null ? counter.get() : 0;
    }

    /**
     * 计算动态有效置信度。
     *
     * <p>公式：effectiveScore = baseConfidence + min(hitCount * 0.05, 0.30)
     * 每命中一次加 0.05，上限封顶 0.30，最终分值不超过 1.0。
     *
     * @param baseConfidence 经验创建时静态设置的置信度，为 null 时取默认值 0.5
     * @param experienceId   经验 ID
     */
    public double getEffectiveScore(Double baseConfidence, String experienceId) {
        double base = baseConfidence != null ? baseConfidence : DEFAULT_BASE_CONFIDENCE;
        double bonus = Math.min(getHitCount(experienceId) * HIT_BONUS_PER_COUNT, MAX_HIT_BONUS);
        return Math.min(base + bonus, 1.0);
    }
}
