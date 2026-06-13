package com.alibaba.assistant.agent.extension.experience.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExperienceUsageTracker 覆盖测试。
 * 验证命中计数、有效置信度公式、边界情况。
 */
class ExperienceUsageTrackerTest {

    private ExperienceUsageTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new ExperienceUsageTracker();
    }

    @Test
    void newExperience_hitCountIsZero() {
        assertThat(tracker.getHitCount("exp-001")).isZero();
    }

    @Test
    void recordHit_incrementsCount() {
        tracker.recordHit("exp-001");
        tracker.recordHit("exp-001");
        tracker.recordHit("exp-001");

        assertThat(tracker.getHitCount("exp-001")).isEqualTo(3);
    }

    @Test
    void multipleExperiences_trackedIndependently() {
        tracker.recordHit("exp-A");
        tracker.recordHit("exp-A");
        tracker.recordHit("exp-B");

        assertThat(tracker.getHitCount("exp-A")).isEqualTo(2);
        assertThat(tracker.getHitCount("exp-B")).isEqualTo(1);
        assertThat(tracker.getHitCount("exp-C")).isZero();
    }

    @Test
    void recordHit_nullOrBlankId_ignoredSafely() {
        tracker.recordHit(null);
        tracker.recordHit("");
        tracker.recordHit("   ");
        // 没有崩溃，且计数为 0
        assertThat(tracker.getHitCount(null)).isZero();
    }

    @Test
    void effectiveScore_noHits_returnsBaseConfidence() {
        // 0 次命中 → bonus = 0 → effectiveScore = baseConfidence
        double score = tracker.getEffectiveScore(0.7, "exp-001");
        assertThat(score).isEqualTo(0.7);
    }

    @Test
    void effectiveScore_nullBaseConfidence_usesDefault() {
        // baseConfidence = null → 默认 0.5
        double score = tracker.getEffectiveScore(null, "exp-new");
        assertThat(score).isEqualTo(0.5);
    }

    @Test
    void effectiveScore_hitsAddBonus() {
        // 命中 2 次 → bonus = 2 * 0.05 = 0.10
        tracker.recordHit("exp-001");
        tracker.recordHit("exp-001");

        double score = tracker.getEffectiveScore(0.6, "exp-001");
        assertThat(score).isEqualTo(0.70, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void effectiveScore_bonusIsCappedAt030() {
        // 命中 10 次 → bonus = 10 * 0.05 = 0.50，但封顶 0.30
        for (int i = 0; i < 10; i++) {
            tracker.recordHit("exp-001");
        }

        double score = tracker.getEffectiveScore(0.5, "exp-001");
        // 0.5 + 0.30 = 0.80
        assertThat(score).isEqualTo(0.80, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void effectiveScore_neverExceedsOnePointZero() {
        // baseConfidence = 0.9 + max bonus 0.30 = 1.20，但封顶 1.0
        for (int i = 0; i < 10; i++) {
            tracker.recordHit("exp-001");
        }

        double score = tracker.getEffectiveScore(0.9, "exp-001");
        assertThat(score).isLessThanOrEqualTo(1.0);
        assertThat(score).isEqualTo(1.0, org.assertj.core.data.Offset.offset(0.001));
    }
}
