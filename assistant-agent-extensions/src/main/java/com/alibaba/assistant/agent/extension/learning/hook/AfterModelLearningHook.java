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

package com.alibaba.assistant.agent.extension.learning.hook;

import com.alibaba.assistant.agent.extension.learning.internal.DefaultLearningContext;
import com.alibaba.assistant.agent.extension.learning.internal.LearningStateExtractor;
import com.alibaba.assistant.agent.extension.learning.internal.DefaultLearningTask;
import com.alibaba.assistant.agent.extension.learning.model.LearningContext;
import com.alibaba.assistant.agent.extension.learning.model.LearningResult;
import com.alibaba.assistant.agent.extension.learning.model.LearningTask;
import com.alibaba.assistant.agent.extension.learning.model.LearningTriggerContext;
import com.alibaba.assistant.agent.extension.learning.model.LearningTriggerSource;
import com.alibaba.assistant.agent.extension.learning.model.*;
import com.alibaba.assistant.agent.extension.learning.spi.LearningExecutor;
import com.alibaba.assistant.agent.extension.learning.spi.LearningStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 模型调用完成后学习Hook
 * 在模型调用完成后触发学习，收集模型调用信息
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@HookPositions(HookPosition.AFTER_MODEL)
public class AfterModelLearningHook extends ModelHook {

	private static final Logger log = LoggerFactory.getLogger(AfterModelLearningHook.class);

	private final LearningExecutor learningExecutor;

	private final LearningStrategy learningStrategy;

	private final String learningType;

	public AfterModelLearningHook(LearningExecutor learningExecutor, LearningStrategy learningStrategy,
			String learningType) {
		this.learningExecutor = learningExecutor;
		this.learningStrategy = learningStrategy;
		this.learningType = learningType != null ? learningType : "model_call";
	}

	@Override
	public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
		try {
			log.debug("AfterModelLearningHook#afterModel - reason=model call completed, starting learning process");

			// 1. 构建学习上下文
			LearningContext context = DefaultLearningContext.builder()
				.overAllState(state)
				.toolCallRecords(LearningStateExtractor.extractToolCallRecords(state))
				.modelCallRecords(LearningStateExtractor.extractModelCallRecords(state))
				.triggerSource(LearningTriggerSource.AFTER_MODEL)
				.build();

			// 2. 构建触发上下文
			LearningTriggerContext triggerContext = LearningTriggerContext.builder()
				.source(LearningTriggerSource.AFTER_MODEL)
				.context(context)
				.build();

			// 3. 判断是否应该触发学习
			if (!learningStrategy.shouldTriggerLearning(triggerContext)) {
				log.info("AfterModelLearningHook#afterModel - reason=strategy decided not to trigger learning");
				return CompletableFuture.completedFuture(Map.of());
			}

			// 4. 构建学习任务
			LearningTask task = DefaultLearningTask.builder()
				.learningType(learningType)
				.triggerSource(LearningTriggerSource.AFTER_MODEL)
				.context(context)
				.build();

			// 5. 执行学习（异步或同步）
			if (learningStrategy.shouldExecuteAsync(task)) {
				log.debug("AfterModelLearningHook#afterModel - reason=executing learning asynchronously, taskId={}",
						task.getId());
				learningExecutor.executeAsync(task).exceptionally(ex -> {
					log.error(
							"AfterModelLearningHook#afterModel - reason=async learning execution failed, taskId={}",
							task.getId(), ex);
					return null;
				});
			}
			else {
				log.debug("AfterModelLearningHook#afterModel - reason=executing learning synchronously, taskId={}",
						task.getId());
				LearningResult result = learningExecutor.execute(task);
				if (!result.isSuccess()) {
					log.warn(
							"AfterModelLearningHook#afterModel - reason=learning execution failed, taskId={}, failureReason={}",
							task.getId(), result.getFailureReason());
				}
			}

		}
		catch (Exception e) {
			// 学习失败不影响主流程
			log.error("AfterModelLearningHook#afterModel - reason=learning hook failed", e);
		}

		return CompletableFuture.completedFuture(Map.of());
	}

	@Override
	public String getName() {
		return "AfterModelLearningHook";
	}

}

