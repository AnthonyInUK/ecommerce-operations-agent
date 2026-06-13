package com.alibaba.assistant.agent.extension.experience.internal;

import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceMetadata;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证经验持久化：写入、按 ID 查找、按类型查找、租户过滤。
 * 使用内存 H2，无需 Spring 上下文。
 */
class JdbcExperienceRepositoryTest {

    private JdbcExperienceRepository repo;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:experience_test_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        repo = new JdbcExperienceRepository(new JdbcTemplate(ds));
    }

    @Test
    void saveAndFindById() {
        Experience exp = new Experience(ExperienceType.COMMON, "日销售额查询", "SELECT sum(pay_amount) FROM dwd_orders WHERE DATE(paid_at) = CURDATE()");
        exp.setDescription("查询当日 GMV");
        repo.save(exp);

        Optional<Experience> found = repo.findById(exp.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("日销售额查询");
        assertThat(found.get().getDescription()).isEqualTo("查询当日 GMV");
        assertThat(found.get().getType()).isEqualTo(ExperienceType.COMMON);
    }

    @Test
    void findByTypeAndTenantId_globalExperienceVisibleToAll() {
        Experience global = new Experience(ExperienceType.COMMON, "全局经验", "content");
        // no tenantId = global
        repo.save(global);

        List<Experience> results = repo.findByTypeAndTenantId(ExperienceType.COMMON, "tenant_a");
        assertThat(results).hasSize(1);
    }

    @Test
    void findByTypeAndTenantId_tenantSpecificNotVisibleToOthers() {
        Experience tenantExp = new Experience(ExperienceType.COMMON, "租户专属", "content");
        ExperienceMetadata meta = new ExperienceMetadata();
        meta.setTenantIdList(List.of("tenant_a"));
        tenantExp.setMetadata(meta);
        repo.save(tenantExp);

        List<Experience> forB = repo.findByTypeAndTenantId(ExperienceType.COMMON, "tenant_b");
        assertThat(forB).isEmpty();

        List<Experience> forA = repo.findByTypeAndTenantId(ExperienceType.COMMON, "tenant_a");
        assertThat(forA).hasSize(1);
    }

    @Test
    void countAndCountByType() {
        repo.save(new Experience(ExperienceType.COMMON, "c1", "x"));
        repo.save(new Experience(ExperienceType.REACT, "r1", "x"));
        repo.save(new Experience(ExperienceType.REACT, "r2", "x"));

        assertThat(repo.count()).isEqualTo(3);
        assertThat(repo.countByType(ExperienceType.COMMON)).isEqualTo(1);
        assertThat(repo.countByType(ExperienceType.REACT)).isEqualTo(2);
    }

    @Test
    void deleteById() {
        Experience exp = new Experience(ExperienceType.TOOL, "tool_exp", "content");
        repo.save(exp);
        assertThat(repo.findById(exp.getId())).isPresent();

        boolean deleted = repo.deleteById(exp.getId());
        assertThat(deleted).isTrue();
        assertThat(repo.findById(exp.getId())).isEmpty();
    }
}
