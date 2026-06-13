package com.alibaba.assistant.agent.extension.experience.internal;

import com.alibaba.assistant.agent.extension.experience.model.AssetEntry;
import com.alibaba.assistant.agent.extension.experience.model.DisclosureStrategy;
import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceArtifact;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceMetadata;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.experience.model.FastIntentConfig;
import com.alibaba.assistant.agent.extension.experience.model.ReferenceEntry;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.util.FileCopyUtils;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * PostgreSQL / H2 持久化经验仓库。
 *
 * <p>复杂字段（tags、metadata、artifact 等）序列化为 JSON 文本列存储，
 * 无需 ORM，与项目已有的 JdbcTemplate 风格保持一致。
 */
public class JdbcExperienceRepository implements ExperienceRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcExperienceRepository.class);

    private static final String TABLE = "assistant_experience";

    private static final String UPSERT_SQL =
            "INSERT INTO " + TABLE + " (id, type, name, description, content, disclosure_strategy, " +
            "tags_json, associated_tools_json, related_experiences_json, " +
            "metadata_json, artifact_json, fast_intent_config_json, references_json, assets_json, " +
            "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
            "ON DUPLICATE KEY UPDATE " +
            "type=VALUES(type), name=VALUES(name), description=VALUES(description), " +
            "content=VALUES(content), disclosure_strategy=VALUES(disclosure_strategy), " +
            "tags_json=VALUES(tags_json), associated_tools_json=VALUES(associated_tools_json), " +
            "related_experiences_json=VALUES(related_experiences_json), " +
            "metadata_json=VALUES(metadata_json), artifact_json=VALUES(artifact_json), " +
            "fast_intent_config_json=VALUES(fast_intent_config_json), " +
            "references_json=VALUES(references_json), assets_json=VALUES(assets_json), " +
            "updated_at=VALUES(updated_at)";

    // H2 (MODE=MySQL) supports ON DUPLICATE KEY UPDATE; PostgreSQL needs ON CONFLICT.
    private static final String UPSERT_SQL_PG =
            "INSERT INTO " + TABLE + " (id, type, name, description, content, disclosure_strategy, " +
            "tags_json, associated_tools_json, related_experiences_json, " +
            "metadata_json, artifact_json, fast_intent_config_json, references_json, assets_json, " +
            "created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
            "ON CONFLICT (id) DO UPDATE SET " +
            "type=EXCLUDED.type, name=EXCLUDED.name, description=EXCLUDED.description, " +
            "content=EXCLUDED.content, disclosure_strategy=EXCLUDED.disclosure_strategy, " +
            "tags_json=EXCLUDED.tags_json, associated_tools_json=EXCLUDED.associated_tools_json, " +
            "related_experiences_json=EXCLUDED.related_experiences_json, " +
            "metadata_json=EXCLUDED.metadata_json, artifact_json=EXCLUDED.artifact_json, " +
            "fast_intent_config_json=EXCLUDED.fast_intent_config_json, " +
            "references_json=EXCLUDED.references_json, assets_json=EXCLUDED.assets_json, " +
            "updated_at=EXCLUDED.updated_at";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final boolean isPostgres;

    public JdbcExperienceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.isPostgres = detectPostgres(jdbc);
        initSchema();
    }

    // -------------------------------------------------------------------------
    // ExperienceRepository implementation
    // -------------------------------------------------------------------------

    @Override
    public Experience save(Experience experience) {
        if (experience.getId() == null) {
            throw new IllegalArgumentException("Experience id must not be null");
        }
        experience.touch();
        jdbc.update(upsertSql(), toParams(experience));
        log.debug("JdbcExperienceRepository#save - id={}", experience.getId());
        return experience;
    }

    @Override
    public List<Experience> batchSave(Collection<Experience> experiences) {
        List<Experience> result = new ArrayList<>();
        for (Experience e : experiences) {
            result.add(save(e));
        }
        return result;
    }

    @Override
    public boolean deleteById(String id) {
        int rows = jdbc.update("DELETE FROM " + TABLE + " WHERE id = ?", id);
        return rows > 0;
    }

    @Override
    public Optional<Experience> findById(String id) {
        List<Experience> rows = jdbc.query(
                "SELECT * FROM " + TABLE + " WHERE id = ?",
                new ExperienceRowMapper(), id);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<Experience> findByTypeAndTenantId(ExperienceType type, String tenantId) {
        List<Experience> all = findAllByType(type);
        if (tenantId == null || tenantId.isBlank()) {
            return all.stream()
                    .filter(e -> e.getMetadata().isGlobal())
                    .toList();
        }
        return all.stream()
                .filter(e -> e.getMetadata().matchesTenantId(tenantId))
                .toList();
    }

    @Override
    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLE, Long.class);
        return n != null ? n : 0L;
    }

    @Override
    public long countByType(ExperienceType type) {
        if (type == null) {
            return count();
        }
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE type = ?", Long.class, type.name());
        return n != null ? n : 0L;
    }

    @Override
    public List<Experience> findAllByType(ExperienceType type) {
        return jdbc.query(
                "SELECT * FROM " + TABLE + " WHERE type = ?",
                new ExperienceRowMapper(), type.name());
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void initSchema() {
        try {
            ClassPathResource resource = new ClassPathResource("db/experience-schema.sql");
            String ddl = FileCopyUtils.copyToString(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
            // Split on semicolons and run each statement — handles multi-statement DDL files.
            for (String stmt : ddl.split(";")) {
                String trimmed = stmt.strip();
                if (!trimmed.isEmpty()) {
                    try {
                        jdbc.execute(trimmed);
                    } catch (Exception e) {
                        log.debug("JdbcExperienceRepository#initSchema - stmt skipped: {}", e.getMessage());
                    }
                }
            }
            log.info("JdbcExperienceRepository#initSchema - reason=经验表初始化完成");
        } catch (Exception e) {
            log.warn("JdbcExperienceRepository#initSchema - reason=初始化失败，将使用已有表结构, error={}", e.getMessage());
        }
    }

    private String upsertSql() {
        return isPostgres ? UPSERT_SQL_PG : UPSERT_SQL;
    }

    private Object[] toParams(Experience e) {
        return new Object[]{
                e.getId(),
                e.getType() != null ? e.getType().name() : null,
                e.getName(),
                e.getDescription(),
                e.getContent(),
                e.getDisclosureStrategy() != null ? e.getDisclosureStrategy().name() : null,
                toJson(e.getTags()),
                toJson(e.getAssociatedTools()),
                toJson(e.getRelatedExperiences()),
                toJson(e.getMetadata()),
                toJson(e.getArtifact()),
                toJson(e.getFastIntentConfig()),
                toJson(e.getReferences()),
                toJson(e.getAssets()),
                Timestamp.from(e.getCreatedAt() != null ? e.getCreatedAt() : Instant.now()),
                Timestamp.from(e.getUpdatedAt() != null ? e.getUpdatedAt() : Instant.now())
        };
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JdbcExperienceRepository#toJson - serialization failed: {}", e.getMessage());
            return null;
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("JdbcExperienceRepository#fromJson - deserialization failed, type={}: {}", type.getSimpleName(), e.getMessage());
            return null;
        }
    }

    private <T> List<T> listFromJson(String json, TypeReference<List<T>> ref) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return mapper.readValue(json, ref);
        } catch (Exception e) {
            log.warn("JdbcExperienceRepository#listFromJson - deserialization failed: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private boolean detectPostgres(JdbcTemplate jdbc) {
        try {
            String url = jdbc.getDataSource() != null
                    ? jdbc.getDataSource().getConnection().getMetaData().getURL()
                    : "";
            return url != null && url.startsWith("jdbc:postgresql");
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // RowMapper
    // -------------------------------------------------------------------------

    private class ExperienceRowMapper implements RowMapper<Experience> {

        @Override
        public Experience mapRow(ResultSet rs, int rowNum) throws SQLException {
            Experience e = new Experience();
            e.setId(rs.getString("id"));

            String typeStr = rs.getString("type");
            if (typeStr != null) {
                try {
                    e.setType(ExperienceType.valueOf(typeStr));
                } catch (IllegalArgumentException ex) {
                    log.warn("JdbcExperienceRepository#mapRow - unknown type: {}", typeStr);
                }
            }

            e.setName(rs.getString("name"));
            e.setDescription(rs.getString("description"));
            e.setContent(rs.getString("content"));

            String strategyStr = rs.getString("disclosure_strategy");
            if (strategyStr != null) {
                try {
                    e.setDisclosureStrategy(DisclosureStrategy.valueOf(strategyStr));
                } catch (IllegalArgumentException ex) {
                    log.warn("JdbcExperienceRepository#mapRow - unknown disclosure_strategy: {}", strategyStr);
                }
            }

            Set<String> tags = new HashSet<>(listFromJson(rs.getString("tags_json"),
                    new TypeReference<List<String>>() {}));
            e.setTags(tags);

            e.setAssociatedTools(listFromJson(rs.getString("associated_tools_json"),
                    new TypeReference<List<String>>() {}));
            e.setRelatedExperiences(listFromJson(rs.getString("related_experiences_json"),
                    new TypeReference<List<String>>() {}));

            ExperienceMetadata metadata = fromJson(rs.getString("metadata_json"), ExperienceMetadata.class);
            e.setMetadata(metadata != null ? metadata : new ExperienceMetadata());

            e.setArtifact(fromJson(rs.getString("artifact_json"), ExperienceArtifact.class));
            e.setFastIntentConfig(fromJson(rs.getString("fast_intent_config_json"), FastIntentConfig.class));

            e.setReferences(listFromJson(rs.getString("references_json"),
                    new TypeReference<List<ReferenceEntry>>() {}));
            e.setAssets(listFromJson(rs.getString("assets_json"),
                    new TypeReference<List<AssetEntry>>() {}));

            Timestamp createdAt = rs.getTimestamp("created_at");
            e.setCreatedAt(createdAt != null ? createdAt.toInstant() : Instant.now());

            Timestamp updatedAt = rs.getTimestamp("updated_at");
            e.setUpdatedAt(updatedAt != null ? updatedAt.toInstant() : Instant.now());

            return e;
        }
    }
}
