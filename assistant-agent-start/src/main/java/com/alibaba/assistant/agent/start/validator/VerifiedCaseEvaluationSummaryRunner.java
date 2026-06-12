package com.alibaba.assistant.agent.start.validator;

import com.alibaba.assistant.agent.extension.experience.fastintent.FastIntentContext;
import com.alibaba.assistant.agent.extension.experience.fastintent.FastIntentService;
import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQuery;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQueryContext;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.experience.model.ReferenceEntry;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import com.alibaba.assistant.agent.start.config.VerifiedCaseCatalog;
import com.alibaba.assistant.agent.start.service.EcommerceQuestionAnswerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
@Order(990)
public class VerifiedCaseEvaluationSummaryRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(VerifiedCaseEvaluationSummaryRunner.class);

    private final VerifiedCaseCatalog verifiedCaseCatalog;
    private final EcommerceQuestionAnswerService questionAnswerService;
    private final ExperienceProvider experienceProvider;
    private final FastIntentService fastIntentService;
    private final ExperienceRepository experienceRepository;

    public VerifiedCaseEvaluationSummaryRunner(VerifiedCaseCatalog verifiedCaseCatalog,
                                               EcommerceQuestionAnswerService questionAnswerService,
                                               ExperienceProvider experienceProvider,
                                               FastIntentService fastIntentService,
                                               ExperienceRepository experienceRepository) {
        this.verifiedCaseCatalog = verifiedCaseCatalog;
        this.questionAnswerService = questionAnswerService;
        this.experienceProvider = experienceProvider;
        this.fastIntentService = fastIntentService;
        this.experienceRepository = experienceRepository;
    }

    @Override
    public void run(String... args) {
        List<Experience> reactExperiences = experienceProvider.query(new ExperienceQuery(ExperienceType.REACT), new ExperienceQueryContext());
        List<CaseEvaluationResult> results = evaluateCases(reactExperiences);
        String runtimeSummary = renderRuntimeSummary(results);
        String runtimeBadCaseSnapshot = renderRuntimeBadCaseSnapshot(results);
        writeBackEvaluationSummary(runtimeSummary);
        writeBackBadCaseSnapshot(runtimeBadCaseSnapshot);
        log.info("VerifiedCaseEvaluationSummaryRunner#run - reason=runtime verified case metrics generated caseCount={}", results.size());
    }

    private List<CaseEvaluationResult> evaluateCases(List<Experience> reactExperiences) {
        List<CaseEvaluationResult> results = new ArrayList<>();
        for (VerifiedCaseCatalog.VerifiedCaseDescriptor descriptor : verifiedCaseCatalog.getCases()) {
            Map<String, Object> answer = questionAnswerService.answer(descriptor.userQuestion());
            Optional<Experience> fastIntentHit = fastIntentService.selectBestMatch(
                    reactExperiences,
                    new FastIntentContext(descriptor.userQuestion(), List.of(), Map.of(), null, null)
            );
            boolean success = Boolean.TRUE.equals(answer.get("success"));
            String actualPathType = String.valueOf(answer.getOrDefault("path_type", ""));
            boolean fastIntentMatched = fastIntentHit.isPresent();
            boolean fastPathHit = "fast".equalsIgnoreCase(descriptor.pathType())
                    && fastIntentMatched
                    && "fast".equalsIgnoreCase(actualPathType);
            boolean deepPathSuccess = "deep".equalsIgnoreCase(descriptor.pathType())
                    && success
                    && "deep".equalsIgnoreCase(actualPathType);
            results.add(new CaseEvaluationResult(
                    descriptor,
                    success,
                    actualPathType,
                    fastIntentMatched,
                    fastPathHit,
                    deepPathSuccess,
                    fastIntentHit.map(Experience::getId).orElse("")
            ));
        }
        return results;
    }

    private String renderRuntimeSummary(List<CaseEvaluationResult> results) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n## Runtime Verified Case Metrics\n\n");
        builder.append(String.format(Locale.ROOT, "- Overall fast path hit rate: %s\n", formatRate(results, "fast", MetricKind.FAST_PATH_HIT)));
        builder.append(String.format(Locale.ROOT, "- Overall deep path success rate: %s\n\n", formatRate(results, "deep", MetricKind.DEEP_PATH_SUCCESS)));
        builder.append("- Top runtime bad case category: ").append(topBadCaseCategory(results)).append("\n\n");

        appendGroupedMetrics(builder, "By Target Role", groupByRole(results));
        appendGroupedMetrics(builder, "By Benchmark Reference", groupByBenchmark(results));
        appendGroupedMetrics(builder, "By Framework Capability", groupByCapability(results));
        appendGroupedBadCaseInterpretation(builder, "By Target Role", groupByRole(results));
        appendGroupedBadCaseInterpretation(builder, "By Benchmark Reference", groupByBenchmark(results));
        appendGroupedBadCaseInterpretation(builder, "By Framework Capability", groupByCapability(results));
        return builder.toString();
    }

    private String renderRuntimeBadCaseSnapshot(List<CaseEvaluationResult> results) {
        Map<BadCaseCategory, List<CaseEvaluationResult>> grouped = groupBadCases(results);
        StringBuilder builder = new StringBuilder();
        builder.append("\nruntime_bad_case_snapshot:\n");
        if (grouped.isEmpty()) {
            builder.append("  - id: bc_runtime_all_green\n");
            builder.append("    symptom: \"当前启动回归样本未发现新的运行时坏例子。\"\n");
            builder.append("    business_risk: \"当前最小 verified case 基线整体可用，但仍需继续扩样本覆盖。\"\n");
            builder.append("    suspected_root_cause: \"暂无新增失败。\"\n");
            builder.append("    fix_direction:\n");
            builder.append("      - \"继续扩大 verified cases 覆盖面\"\n");
            builder.append("      - \"持续观察新引入功能后的回归结果\"\n");
            return builder.toString();
        }

        for (Map.Entry<BadCaseCategory, List<CaseEvaluationResult>> entry : grouped.entrySet()) {
            BadCaseCategory category = entry.getKey();
            List<CaseEvaluationResult> categoryResults = entry.getValue();
            CaseEvaluationResult example = categoryResults.get(0);
            builder.append("  - id: ").append(category.id).append("\n");
            builder.append("    symptom: \"").append(escapeYaml(category.renderSymptom(categoryResults, example))).append("\"\n");
            builder.append("    business_risk: \"").append(escapeYaml(category.businessRisk)).append("\"\n");
            builder.append("    suspected_root_cause: \"").append(escapeYaml(category.suspectedRootCause)).append("\"\n");
            builder.append("    fix_direction:\n");
            for (String fixDirection : category.fixDirection) {
                builder.append("      - \"").append(escapeYaml(fixDirection)).append("\"\n");
            }
        }
        return builder.toString();
    }

    private void appendGroupedMetrics(StringBuilder builder, String title, Map<String, List<CaseEvaluationResult>> groups) {
        builder.append("### ").append(title).append("\n\n");
        for (Map.Entry<String, List<CaseEvaluationResult>> entry : groups.entrySet()) {
            String fastRate = formatRate(entry.getValue(), "fast", MetricKind.FAST_PATH_HIT);
            String deepRate = formatRate(entry.getValue(), "deep", MetricKind.DEEP_PATH_SUCCESS);
            builder.append("- ").append(entry.getKey())
                    .append(": fast path hit rate=").append(fastRate)
                    .append(", deep path success rate=").append(deepRate)
                    .append("\n");
        }
        builder.append("\n");
    }

    private void appendGroupedBadCaseInterpretation(StringBuilder builder,
                                                    String title,
                                                    Map<String, List<CaseEvaluationResult>> groups) {
        builder.append("### Runtime Bad Case Interpretation ").append(title).append("\n\n");
        boolean hasAnyFailure = false;
        for (Map.Entry<String, List<CaseEvaluationResult>> entry : groups.entrySet()) {
            Map<BadCaseCategory, List<CaseEvaluationResult>> groupedBadCases = groupBadCases(entry.getValue());
            if (groupedBadCases.isEmpty()) {
                continue;
            }
            hasAnyFailure = true;
            String topCategory = groupedBadCases.entrySet().stream()
                    .max(java.util.Comparator.comparingInt(candidate -> candidate.getValue().size()))
                    .map(candidate -> candidate.getKey().displayName + " (" + candidate.getValue().size() + ")")
                    .orElse("None");
            builder.append("- ").append(entry.getKey())
                    .append(": top bad case=").append(topCategory)
                    .append("，说明这一组样本当前最需要优先修复相关路径。\n");
        }
        if (!hasAnyFailure) {
            builder.append("- 当前没有新的 runtime bad case，说明这轮样本在该分组下整体稳定。\n");
        }
        builder.append("\n");
    }

    private Map<String, List<CaseEvaluationResult>> groupByRole(List<CaseEvaluationResult> results) {
        return groupBy(results, result -> result.descriptor().targetRole());
    }

    private Map<String, List<CaseEvaluationResult>> groupByBenchmark(List<CaseEvaluationResult> results) {
        return groupBy(results, result -> result.descriptor().benchmarkReference());
    }

    private Map<String, List<CaseEvaluationResult>> groupByCapability(List<CaseEvaluationResult> results) {
        Map<String, List<CaseEvaluationResult>> grouping = new LinkedHashMap<>();
        for (CaseEvaluationResult result : results) {
            for (String capability : result.descriptor().frameworkCapabilities()) {
                grouping.computeIfAbsent(capability, ignored -> new ArrayList<>()).add(result);
            }
        }
        return grouping;
    }

    private Map<String, List<CaseEvaluationResult>> groupBy(List<CaseEvaluationResult> results,
                                                             java.util.function.Function<CaseEvaluationResult, String> keyExtractor) {
        Map<String, List<CaseEvaluationResult>> grouping = new LinkedHashMap<>();
        for (CaseEvaluationResult result : results) {
            grouping.computeIfAbsent(keyExtractor.apply(result), ignored -> new ArrayList<>()).add(result);
        }
        return grouping;
    }

    private String formatRate(List<CaseEvaluationResult> results, String expectedPathType, MetricKind metricKind) {
        long denominator = results.stream()
                .filter(result -> expectedPathType.equalsIgnoreCase(result.descriptor().pathType()))
                .count();
        if (denominator == 0) {
            return "N/A";
        }
        long numerator = results.stream()
                .filter(result -> expectedPathType.equalsIgnoreCase(result.descriptor().pathType()))
                .filter(result -> metricKind == MetricKind.FAST_PATH_HIT ? result.fastPathHit() : result.deepPathSuccess())
                .count();
        return String.format(Locale.ROOT, "%.0f%% (%d/%d)", numerator * 100.0 / denominator, numerator, denominator);
    }

    private String topBadCaseCategory(List<CaseEvaluationResult> results) {
        Map<BadCaseCategory, List<CaseEvaluationResult>> grouped = groupBadCases(results);
        if (grouped.isEmpty()) {
            return "None";
        }
        return grouped.entrySet().stream()
                .max(java.util.Comparator.comparingInt(entry -> entry.getValue().size()))
                .map(entry -> entry.getKey().displayName + " (" + entry.getValue().size() + ")")
                .orElse("None");
    }

    private Map<BadCaseCategory, List<CaseEvaluationResult>> groupBadCases(List<CaseEvaluationResult> results) {
        Map<BadCaseCategory, List<CaseEvaluationResult>> grouped = new LinkedHashMap<>();
        for (CaseEvaluationResult result : results) {
            BadCaseCategory category = classifyBadCase(result);
            if (category == null) {
                continue;
            }
            grouped.computeIfAbsent(category, ignored -> new ArrayList<>()).add(result);
        }
        return grouped;
    }

    private BadCaseCategory classifyBadCase(CaseEvaluationResult result) {
        if ("fast".equalsIgnoreCase(result.descriptor().pathType()) && !result.fastIntentMatched()) {
            return BadCaseCategory.FAST_INTENT_MISS;
        }
        if ("fast".equalsIgnoreCase(result.descriptor().pathType())
                && result.fastIntentMatched()
                && (!result.success() || !result.fastPathHit())) {
            return BadCaseCategory.FAST_PATH_EXECUTION_GAP;
        }
        if ("deep".equalsIgnoreCase(result.descriptor().pathType()) && !result.success()) {
            return BadCaseCategory.DEEP_PATH_EXECUTION_GAP;
        }
        if ("deep".equalsIgnoreCase(result.descriptor().pathType())
                && result.success()
                && !"deep".equalsIgnoreCase(result.actualPathType())) {
            return BadCaseCategory.DEEP_PATH_TYPE_MISMATCH;
        }
        return null;
    }

    private void writeBackEvaluationSummary(String runtimeSummary) {
        Experience experience = experienceRepository.findById("exp-ecom-evaluation-loop").orElse(null);
        if (experience == null) {
            log.warn("VerifiedCaseEvaluationSummaryRunner#writeBackEvaluationSummary - reason=evaluation loop experience missing");
            return;
        }
        for (ReferenceEntry reference : experience.getReferences()) {
            if (!"experiences/evaluation_summary.md".equals(reference.getPath())) {
                continue;
            }
            String current = reference.getContent() == null ? "" : reference.getContent();
            String base = current.contains("## Runtime Verified Case Metrics")
                    ? current.substring(0, current.indexOf("## Runtime Verified Case Metrics")).stripTrailing()
                    : current.stripTrailing();
            String updated = base + runtimeSummary;
            reference.setContent(updated);
            reference.setSize((long) updated.getBytes(StandardCharsets.UTF_8).length);
            reference.setContentHash(Integer.toHexString(updated.hashCode()));
        }
        experienceRepository.save(experience);
    }

    private void writeBackBadCaseSnapshot(String runtimeBadCaseSnapshot) {
        Experience experience = experienceRepository.findById("exp-ecom-evaluation-loop").orElse(null);
        if (experience == null) {
            log.warn("VerifiedCaseEvaluationSummaryRunner#writeBackBadCaseSnapshot - reason=evaluation loop experience missing");
            return;
        }
        for (ReferenceEntry reference : experience.getReferences()) {
            if (!"experiences/bad_cases.yaml".equals(reference.getPath())) {
                continue;
            }
            String current = reference.getContent() == null ? "" : reference.getContent();
            String marker = "\nruntime_bad_case_snapshot:";
            String base = current.contains(marker)
                    ? current.substring(0, current.indexOf(marker)).stripTrailing()
                    : current.stripTrailing();
            String updated = base + runtimeBadCaseSnapshot;
            reference.setContent(updated);
            reference.setSize((long) updated.getBytes(StandardCharsets.UTF_8).length);
            reference.setContentHash(Integer.toHexString(updated.hashCode()));
        }
        experienceRepository.save(experience);
    }

    private String escapeYaml(String value) {
        return value.replace("\"", "\\\"");
    }

    private enum MetricKind {
        FAST_PATH_HIT,
        DEEP_PATH_SUCCESS
    }

    private enum BadCaseCategory {
        FAST_INTENT_MISS(
                "bc_runtime_fast_intent_miss",
                "FastIntent 漏命中",
                "高频标准问题没有命中快路径，会让系统多走一轮理解和规划，拖慢响应并削弱稳定性。",
                "FastIntent 规则、别名或 verified case 覆盖还不够完整。",
                List.of(
                        "补充高频问题问法和别名",
                        "把失败问题回流进 verified cases / FastIntent 规则"
                )
        ) {
            @Override
            String renderSymptom(List<CaseEvaluationResult> results, CaseEvaluationResult example) {
                return String.format(Locale.ROOT,
                        "共有 %d 条快路径样本没有命中 FastIntent，代表问题包括：%s。",
                        results.size(),
                        example.descriptor().id());
            }
        },
        FAST_PATH_EXECUTION_GAP(
                "bc_runtime_fast_path_execution_gap",
                "快路径命中但执行未闭环",
                "系统虽然认出了标准问题，但没有稳定走完整条快路径，会让业务误以为高频问题已经产品化。",
                "FastIntent 命中了 Experience，但问答链、Tool 执行或 path type 回写没有完全对齐。",
                List.of(
                        "校验 FastIntent 命中后的 path_type 和 Tool 链是否一致",
                        "补充快路径标准回答结构和执行回归样本"
                )
        ) {
            @Override
            String renderSymptom(List<CaseEvaluationResult> results, CaseEvaluationResult example) {
                return String.format(Locale.ROOT,
                        "共有 %d 条快路径样本命中了标准经验，但执行结果没有稳定闭环，代表问题包括：%s。",
                        results.size(),
                        example.descriptor().id());
            }
        },
        DEEP_PATH_EXECUTION_GAP(
                "bc_runtime_deep_path_execution_gap",
                "深路径分析链中断",
                "复杂归因问题如果走不完整条分析链，业务仍然拿不到可执行结论，看起来像答了问题但没有真正定位原因。",
                "深路径 Tool 链、会话承接或数据口径仍有缺口。",
                List.of(
                        "优先检查 root cause 标准链每一步是否都有稳定数据承接",
                        "把失败样本沉淀成 bad case，逐步补全深路径约束"
                )
        ) {
            @Override
            String renderSymptom(List<CaseEvaluationResult> results, CaseEvaluationResult example) {
                return String.format(Locale.ROOT,
                        "共有 %d 条深路径样本执行失败，代表问题包括：%s。",
                        results.size(),
                        example.descriptor().id());
            }
        },
        DEEP_PATH_TYPE_MISMATCH(
                "bc_runtime_deep_path_type_mismatch",
                "深路径被误走成其它路径",
                "复杂归因问题如果被误降级成快路径或普通问答，分析深度会不够，容易漏掉订单结构、用户规模或退款等关键因素。",
                "路径判断和实际问答链返回的 path_type 没有完全对齐。",
                List.of(
                        "收紧深路径问题识别规则",
                        "让 root cause 类 verified case 更明确绑定标准 REACT plan"
                )
        ) {
            @Override
            String renderSymptom(List<CaseEvaluationResult> results, CaseEvaluationResult example) {
                return String.format(Locale.ROOT,
                        "共有 %d 条深路径样本虽然返回成功，但 path type 不符合预期，代表问题包括：%s。",
                        results.size(),
                        example.descriptor().id());
            }
        };

        private final String id;
        private final String displayName;
        private final String businessRisk;
        private final String suspectedRootCause;
        private final List<String> fixDirection;

        BadCaseCategory(String id,
                        String displayName,
                        String businessRisk,
                        String suspectedRootCause,
                        List<String> fixDirection) {
            this.id = id;
            this.displayName = displayName;
            this.businessRisk = businessRisk;
            this.suspectedRootCause = suspectedRootCause;
            this.fixDirection = fixDirection;
        }

        abstract String renderSymptom(List<CaseEvaluationResult> results, CaseEvaluationResult example);
    }

    private record CaseEvaluationResult(
            VerifiedCaseCatalog.VerifiedCaseDescriptor descriptor,
            boolean success,
            String actualPathType,
            boolean fastIntentMatched,
            boolean fastPathHit,
            boolean deepPathSuccess,
            String matchedExperienceId
    ) {
    }
}
