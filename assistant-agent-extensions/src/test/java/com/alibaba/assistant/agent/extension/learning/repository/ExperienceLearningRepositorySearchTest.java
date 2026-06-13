package com.alibaba.assistant.agent.extension.learning.repository;

import com.alibaba.assistant.agent.extension.experience.internal.InMemoryExperienceRepository;
import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceMetadata;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.learning.model.LearningSearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExperienceLearningRepository.search() 覆盖测试。
 * 验证类型过滤、租户隔离、关键字搜索、时间范围、分页和边界情况。
 */
class ExperienceLearningRepositorySearchTest {

    private InMemoryExperienceRepository backingRepo;
    private ExperienceLearningRepository repo;

    @BeforeEach
    void setUp() {
        backingRepo = new InMemoryExperienceRepository();
        repo = new ExperienceLearningRepository(backingRepo);
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private Experience globalExperience(ExperienceType type, String name, String content) {
        Experience e = new Experience(type, name, content);
        e.setCreatedAt(Instant.now());
        return e;
    }

    private Experience tenantExperience(ExperienceType type, String name, String content, String tenantId) {
        Experience e = new Experience(type, name, content);
        ExperienceMetadata meta = new ExperienceMetadata();
        meta.addTenantId(tenantId);
        e.setMetadata(meta);
        e.setCreatedAt(Instant.now());
        return e;
    }

    // ------------------------------------------------------------------
    // 测试用例
    // ------------------------------------------------------------------

    @Test
    void nullRequest_returnsEmptyList() {
        backingRepo.save(globalExperience(ExperienceType.COMMON, "任意经验", "内容"));
        assertThat(repo.search(null)).isEmpty();
    }

    @Test
    void search_byType_returnsOnlyMatchingType() {
        backingRepo.save(globalExperience(ExperienceType.COMMON, "公共经验", "内容"));
        backingRepo.save(globalExperience(ExperienceType.TOOL, "工具经验", "内容"));
        backingRepo.save(globalExperience(ExperienceType.REACT, "React经验", "内容"));

        LearningSearchRequest req = LearningSearchRequest.builder()
                .learningType("COMMON")
                .limit(10)
                .build();

        List<Experience> result = repo.search(req);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getType()).isEqualTo(ExperienceType.COMMON);
    }

    @Test
    void search_unknownType_queriesAllTypes() {
        backingRepo.save(globalExperience(ExperienceType.COMMON, "公共经验", "内容"));
        backingRepo.save(globalExperience(ExperienceType.TOOL, "工具经验", "内容"));

        LearningSearchRequest req = LearningSearchRequest.builder()
                .learningType("NONEXISTENT")
                .limit(10)
                .build();

        // 未知类型退化为 null → 查询全部类型
        List<Experience> result = repo.search(req);
        assertThat(result).hasSize(2);
    }

    @Test
    void search_withNamespace_isolatesGlobalAndTenantExperiences() {
        // 全局经验（所有人可见）
        backingRepo.save(globalExperience(ExperienceType.COMMON, "全局经验", "内容"));
        // 租户 A 专属
        backingRepo.save(tenantExperience(ExperienceType.COMMON, "租户A经验", "内容", "tenantA"));
        // 租户 B 专属
        backingRepo.save(tenantExperience(ExperienceType.COMMON, "租户B经验", "内容", "tenantB"));

        // 以租户 A 身份搜索：应看到全局 + 租户A
        List<Experience> tenantAResult = repo.search(
                LearningSearchRequest.builder().namespace("tenantA").limit(10).build());
        assertThat(tenantAResult).hasSize(2);
        assertThat(tenantAResult).extracting(Experience::getName)
                .containsExactlyInAnyOrder("全局经验", "租户A经验");

        // 不带租户（namespace=null）：只看到全局经验
        List<Experience> globalResult = repo.search(
                LearningSearchRequest.builder().limit(10).build());
        assertThat(globalResult).hasSize(1);
        assertThat(globalResult.get(0).getName()).isEqualTo("全局经验");
    }

    @Test
    void search_withKeyword_filtersOnNameAndContent() {
        Experience sqlExp = globalExperience(ExperienceType.COMMON, "SQL查询技巧", "使用索引提升查询速度");
        Experience msgExp = globalExperience(ExperienceType.COMMON, "消息推送", "通过WebSocket实现实时推送");
        backingRepo.save(sqlExp);
        backingRepo.save(msgExp);

        LearningSearchRequest req = LearningSearchRequest.builder()
                .query("sql")   // 大小写不敏感
                .limit(10)
                .build();

        List<Experience> result = repo.search(req);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("SQL查询技巧");
    }

    @Test
    void search_withKeyword_matchesTags() {
        Experience e = globalExperience(ExperienceType.TOOL, "工具使用", "内容");
        e.addTag("database");
        e.addTag("index");
        backingRepo.save(e);

        backingRepo.save(globalExperience(ExperienceType.TOOL, "另一个工具", "无关内容"));

        List<Experience> result = repo.search(
                LearningSearchRequest.builder().query("database").limit(10).build());
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("工具使用");
    }

    @Test
    void search_withTimeRange_filtersCreatedAt() {
        Instant now = Instant.now();

        Experience old = globalExperience(ExperienceType.COMMON, "旧经验", "内容");
        old.setCreatedAt(now.minus(10, ChronoUnit.DAYS));
        backingRepo.save(old);

        Experience recent = globalExperience(ExperienceType.COMMON, "新经验", "内容");
        recent.setCreatedAt(now.minus(1, ChronoUnit.HOURS));
        backingRepo.save(recent);

        // 只查最近 3 天
        LearningSearchRequest req = LearningSearchRequest.builder()
                .timeRangeStart(now.minus(3, ChronoUnit.DAYS))
                .limit(10)
                .build();

        List<Experience> result = repo.search(req);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("新经验");
    }

    @Test
    void search_withLimitAndOffset_paginates() {
        for (int i = 1; i <= 5; i++) {
            backingRepo.save(globalExperience(ExperienceType.COMMON, "经验" + i, "内容" + i));
        }

        // 第一页：前 2 条
        List<Experience> page1 = repo.search(
                LearningSearchRequest.builder().limit(2).offset(0).build());
        assertThat(page1).hasSize(2);

        // 第二页：跳过 2 条，取接下来 2 条
        List<Experience> page2 = repo.search(
                LearningSearchRequest.builder().limit(2).offset(2).build());
        assertThat(page2).hasSize(2);

        // 两页不重叠
        assertThat(page1).extracting(Experience::getName)
                .doesNotContainAnyElementsOf(
                        page2.stream().map(Experience::getName).toList());

        // 最后一页只剩 1 条
        List<Experience> page3 = repo.search(
                LearningSearchRequest.builder().limit(2).offset(4).build());
        assertThat(page3).hasSize(1);
    }

    @Test
    void search_emptyRepo_returnsEmptyList() {
        List<Experience> result = repo.search(
                LearningSearchRequest.builder().learningType("COMMON").limit(10).build());
        assertThat(result).isEmpty();
    }

}
