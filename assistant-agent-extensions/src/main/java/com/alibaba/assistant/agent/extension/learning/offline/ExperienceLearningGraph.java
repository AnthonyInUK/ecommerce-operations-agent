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

package com.alibaba.assistant.agent.extension.learning.offline;

import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import com.alibaba.assistant.agent.extension.learning.extractor.ExperienceLearningExtractor;
import com.alibaba.assistant.agent.extension.learning.internal.DefaultLearningContext;
import com.alibaba.assistant.agent.extension.learning.internal.JdbcLearningSessionRepository;
import com.alibaba.assistant.agent.extension.learning.model.LearningContext;
import com.alibaba.assistant.agent.extension.learning.model.LearningSessionRecord;
import com.alibaba.assistant.agent.extension.learning.model.LearningTriggerSource;
import com.alibaba.assistant.agent.extension.learning.model.ModelCallRecord;
import com.alibaba.assistant.agent.extension.learning.model.ToolCallRecord;
import com.alibaba.assistant.agent.extension.learning.spi.LearningExecutor;
import com.alibaba.assistant.agent.extension.learning.spi.LearningExtractor;
import com.alibaba.assistant.agent.extension.learning.spi.LearningRepository;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 经验学习图
 * 专门用于从历史数据中学习经验的离线学习图
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ExperienceLearningGraph extends OfflineLearningGraph {

	private static final Logger log = LoggerFactory.getLogger(ExperienceLearningGraph.class);

	private final ExperienceLearningExtractor experienceExtractor;

	private final ExperienceRepository experienceRepository;

	private final JdbcLearningSessionRepository sessionRepository;

	private final long lookbackPeriodHours;

	private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

	public ExperienceLearningGraph(LearningExecutor learningExecutor, List<LearningExtractor<?>> extractors,
			List<LearningRepository<?>> repositories, ExperienceLearningExtractor experienceExtractor,
			ExperienceRepository experienceRepository, long lookbackPeriodHours) {
		this(learningExecutor, extractors, repositories, experienceExtractor,
				experienceRepository, lookbackPeriodHours, null);
	}

	public ExperienceLearningGraph(LearningExecutor learningExecutor, List<LearningExtractor<?>> extractors,
			List<LearningRepository<?>> repositories, ExperienceLearningExtractor experienceExtractor,
			ExperienceRepository experienceRepository, long lookbackPeriodHours,
			JdbcLearningSessionRepository sessionRepository) {
		super(learningExecutor, extractors, repositories, "experience_learning_graph");
		this.experienceExtractor = experienceExtractor;
		this.experienceRepository = experienceRepository;
		this.lookbackPeriodHours = lookbackPeriodHours > 0 ? lookbackPeriodHours : 24;
		this.sessionRepository = sessionRepository;
	}

	@Override
	protected List<Object> fetchHistoricalData(RunnableConfig config) {
		log.info("ExperienceLearningGraph#fetchHistoricalData - reason=fetching session logs, lookbackHours={}",
				lookbackPeriodHours);

		if (sessionRepository == null) {
			log.warn("ExperienceLearningGraph#fetchHistoricalData - reason=no JdbcLearningSessionRepository available, offline learning skipped");
			return List.of();
		}

		try {
			Instant cutoffTime = Instant.now().minus(lookbackPeriodHours, ChronoUnit.HOURS);
			List<LearningSessionRecord> records = sessionRepository.findUnprocessedSince(cutoffTime);
			log.info("ExperienceLearningGraph#fetchHistoricalData - reason=session logs fetched, count={}", records.size());
			return new ArrayList<>(records);
		} catch (Exception e) {
			log.error("ExperienceLearningGraph#fetchHistoricalData - reason=failed to fetch session logs", e);
			return List.of();
		}
	}

	@Override
	protected List<Object> preprocessData(List<Object> rawData, RunnableConfig config) {
		log.info("ExperienceLearningGraph#preprocessData - reason=filtering session logs, count={}", rawData.size());

		// 过滤掉没有任何实质内容的会话（工具调用和代码生成都是空的），没有可学习的内容
		List<Object> processedData = rawData.stream().filter(data -> {
			if (!(data instanceof LearningSessionRecord record)) return false;
			boolean hasToolCalls = record.getToolCallsJson() != null && !record.getToolCallsJson().equals("[]");
			boolean hasModelCalls = record.getModelCallsJson() != null && !record.getModelCallsJson().equals("[]");
			return hasToolCalls || hasModelCalls;
		}).collect(Collectors.toList());

		log.info("ExperienceLearningGraph#preprocessData - reason=filter completed, original={}, kept={}",
				rawData.size(), processedData.size());
		return processedData;
	}

	@Override
	protected List<Object> extractLearningRecords(List<Object> processedData, RunnableConfig config) {
		log.info("ExperienceLearningGraph#extractLearningRecords - reason=extracting experiences, count={}",
				processedData.size());

		try {
			List<Object> allExperiences = new ArrayList<>();

			for (Object data : processedData) {
				// 将历史数据转换为LearningContext
				LearningContext context = convertToLearningContext(data);

				// 检查是否应该学习
				if (!experienceExtractor.shouldLearn(context)) {
					continue;
				}

				// 提取经验
				List<Experience> experiences = experienceExtractor.extract(context);
				allExperiences.addAll(experiences);
			}

			// 无论提取到多少经验，都标记这批会话已被离线处理过，避免下次重复消费
			if (sessionRepository != null) {
				List<String> processedIds = processedData.stream()
						.filter(d -> d instanceof LearningSessionRecord)
						.map(d -> ((LearningSessionRecord) d).getId())
						.toList();
				sessionRepository.markProcessed(processedIds);
			}

			log.info("ExperienceLearningGraph#extractLearningRecords - reason=extraction completed, input={}, extracted={}",
					processedData.size(), allExperiences.size());

			return allExperiences;
		}
		catch (Exception e) {
			log.error("ExperienceLearningGraph#extractLearningRecords - reason=extraction failed", e);
			return List.of();
		}
	}

	@Override
	protected int persistLearningRecords(List<Object> records, RunnableConfig config) {
		log.info("ExperienceLearningGraph#persistLearningRecords - reason=persisting experiences, count={}",
				records.size());

		try {
			int persistedCount = 0;

			for (Object record : records) {
				if (record instanceof Experience) {
					Experience experience = (Experience) record;
					experienceRepository.save(experience);
					persistedCount++;
				}
				else {
					log.warn(
							"ExperienceLearningGraph#persistLearningRecords - reason=skipping non-experience record, type={}",
							record.getClass().getName());
				}
			}

			log.info(
					"ExperienceLearningGraph#persistLearningRecords - reason=persistence completed, total={}, persisted={}",
					records.size(), persistedCount);

			return persistedCount;
		}
		catch (Exception e) {
			log.error("ExperienceLearningGraph#persistLearningRecords - reason=persistence failed", e);
			return 0;
		}
	}

	/**
	 * 将 LearningSessionRecord 还原为 LearningContext，供 ExperienceLearningExtractor 判断。
	 */
	private LearningContext convertToLearningContext(Object data) {
		if (!(data instanceof LearningSessionRecord record)) {
			return DefaultLearningContext.builder().triggerSource(LearningTriggerSource.SCHEDULED).build();
		}

		List<ToolCallRecord> toolCalls = new ArrayList<>();
		List<ModelCallRecord> modelCalls = new ArrayList<>();

		try {
			if (record.getToolCallsJson() != null && !record.getToolCallsJson().isBlank()) {
				toolCalls = mapper.readValue(record.getToolCallsJson(), new TypeReference<>() {});
			}
		} catch (Exception e) {
			log.debug("ExperienceLearningGraph#convertToLearningContext - toolCalls parse failed: {}", e.getMessage());
		}

		try {
			if (record.getModelCallsJson() != null && !record.getModelCallsJson().isBlank()) {
				modelCalls = mapper.readValue(record.getModelCallsJson(), new TypeReference<>() {});
			}
		} catch (Exception e) {
			log.debug("ExperienceLearningGraph#convertToLearningContext - modelCalls parse failed: {}", e.getMessage());
		}

		return DefaultLearningContext.builder()
				.toolCallRecords(toolCalls)
				.modelCallRecords(modelCalls)
				.triggerSource(LearningTriggerSource.SCHEDULED)
				.build();
	}

}

