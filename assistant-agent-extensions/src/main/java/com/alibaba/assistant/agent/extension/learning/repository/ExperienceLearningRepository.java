/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.assistant.agent.extension.learning.repository;

import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import com.alibaba.assistant.agent.extension.learning.model.LearningSearchRequest;
import com.alibaba.assistant.agent.extension.learning.spi.LearningRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * 经验学习仓库
 * 将学习到的经验保存到经验仓库中，实现学习模块与经验模块的集成
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ExperienceLearningRepository implements LearningRepository<Experience> {

	private static final Logger log = LoggerFactory.getLogger(ExperienceLearningRepository.class);

	private final ExperienceRepository experienceRepository;

	public ExperienceLearningRepository(ExperienceRepository experienceRepository) {
		this.experienceRepository = experienceRepository;
	}

	@Override
	public void save(String namespace, String key, Experience record) {
		if (record == null) {
			log.warn("ExperienceLearningRepository#save - reason=record is null");
			return;
		}

		try {
			experienceRepository.save(record);
			log.info("ExperienceLearningRepository#save - reason=experience saved successfully, namespace={}, key={}",
					namespace, key);
		}
		catch (Exception e) {
			log.error(
					"ExperienceLearningRepository#save - reason=failed to save experience, namespace={}, key={}",
					namespace, key, e);
		}
	}

	@Override
	public void saveBatch(String namespace, List<Experience> records) {
		if (records == null || records.isEmpty()) {
			log.warn("ExperienceLearningRepository#saveBatch - reason=records is empty, namespace={}", namespace);
			return;
		}

		try {
			experienceRepository.batchSave(records);
			log.info(
					"ExperienceLearningRepository#saveBatch - reason=experiences saved successfully, namespace={}, count={}",
					namespace, records.size());
		}
		catch (Exception e) {
			log.error(
					"ExperienceLearningRepository#saveBatch - reason=failed to save experiences, namespace={}, count={}",
					namespace, records.size(), e);
		}
	}

	@Override
	public Experience get(String namespace, String key) {
		try {
			return experienceRepository.findById(key).orElse(null);
		}
		catch (Exception e) {
			log.error("ExperienceLearningRepository#get - reason=failed to get experience, namespace={}, key={}",
					namespace, key, e);
			return null;
		}
	}

	@Override
	public List<Experience> search(LearningSearchRequest request) {
		if (request == null) {
			return List.of();
		}

		try {
			// 1. Resolve ExperienceType from learningType string (null = all types)
			ExperienceType type = resolveType(request.getLearningType());

			// 2. Fetch from repository: by type+tenant when both are present
			List<Experience> candidates;
			String namespace = request.getNamespace();
			if (type != null) {
				candidates = experienceRepository.findByTypeAndTenantId(type, namespace);
			} else {
				// All types: aggregate across every ExperienceType, then filter by tenant
				candidates = java.util.Arrays.stream(ExperienceType.values())
						.flatMap(t -> experienceRepository.findByTypeAndTenantId(t, namespace).stream())
						.toList();
			}

			// 3. In-memory filters: keyword and time range
			String query = request.getQuery();
			candidates = candidates.stream()
					.filter(e -> matchesQuery(e, query))
					.filter(e -> request.getTimeRangeStart() == null
							|| (e.getCreatedAt() != null && !e.getCreatedAt().isBefore(request.getTimeRangeStart())))
					.filter(e -> request.getTimeRangeEnd() == null
							|| (e.getCreatedAt() != null && !e.getCreatedAt().isAfter(request.getTimeRangeEnd())))
					.toList();

			// 4. Pagination
			int offset = Math.max(0, request.getOffset());
			int limit = request.getLimit() > 0 ? request.getLimit() : 10;
			List<Experience> page = candidates.stream()
					.skip(offset)
					.limit(limit)
					.toList();

			log.info("ExperienceLearningRepository#search - total={}, returned={}, namespace={}, type={}",
					candidates.size(), page.size(), namespace, type);
			return page;

		} catch (Exception e) {
			log.error("ExperienceLearningRepository#search - reason=search failed", e);
			return List.of();
		}
	}

	private ExperienceType resolveType(String learningType) {
		if (!StringUtils.hasText(learningType)) {
			return null;
		}
		try {
			return ExperienceType.valueOf(learningType.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			log.warn("ExperienceLearningRepository#resolveType - unknown type: {}, ignoring", learningType);
			return null;
		}
	}

	private boolean matchesQuery(Experience experience, String query) {
		if (!StringUtils.hasText(query)) {
			return true;
		}
		String q = query.toLowerCase(Locale.ROOT);
		return (experience.getName() != null && experience.getName().toLowerCase(Locale.ROOT).contains(q))
				|| (experience.getDescription() != null && experience.getDescription().toLowerCase(Locale.ROOT).contains(q))
				|| (experience.getContent() != null && experience.getContent().toLowerCase(Locale.ROOT).contains(q))
				|| (experience.getTags() != null && experience.getTags().stream()
						.anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(q)));
	}

	@Override
	public void delete(String namespace, String key) {
		try {
			boolean deleted = experienceRepository.deleteById(key);
			log.info(
					"ExperienceLearningRepository#delete - reason=delete operation completed, namespace={}, key={}, deleted={}",
					namespace, key, deleted);
		}
		catch (Exception e) {
			log.error("ExperienceLearningRepository#delete - reason=failed to delete experience, namespace={}, key={}",
					namespace, key, e);
		}
	}

	@Override
	public Class<Experience> getSupportedRecordType() {
		return Experience.class;
	}

}
