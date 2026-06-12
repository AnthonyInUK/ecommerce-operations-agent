package com.alibaba.assistant.agent.start.config;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class VerifiedCaseCatalog {

    private final List<VerifiedCaseDescriptor> cases;

    public VerifiedCaseCatalog(ResourceLoader resourceLoader) {
        this.cases = List.copyOf(loadCases(resourceLoader));
    }

    public List<VerifiedCaseDescriptor> getCases() {
        return cases;
    }

    public List<VerifiedCaseDescriptor> findRelevantCases(String query, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        String normalized = normalize(query);
        return cases.stream()
                .map(descriptor -> new ScoredVerifiedCase(descriptor, score(descriptor, normalized)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingInt(ScoredVerifiedCase::score).reversed()
                        .thenComparing(scored -> scored.descriptor().id()))
                .limit(Math.max(limit, 1))
                .map(ScoredVerifiedCase::descriptor)
                .toList();
    }

    public String renderPromptBrief(String query, int limit) {
        List<VerifiedCaseDescriptor> relevantCases = findRelevantCases(query, limit);
        if (relevantCases.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("<verified_case_signal_summary>\n");
        builder.append("结合 verified cases 标签，当前问题最相关的已验证样本有：\n");
        for (VerifiedCaseDescriptor descriptor : relevantCases) {
            builder.append("- ")
                    .append(descriptor.id())
                    .append(" | 角色=")
                    .append(descriptor.targetRole())
                    .append(" | 对标痛点=")
                    .append(descriptor.benchmarkReference())
                    .append(" | 框架能力=")
                    .append(String.join(", ", descriptor.frameworkCapabilities()))
                    .append("\n");
        }
        builder.append("如果当前问题在讲产品定位、业务价值、竞品差异或框架能力，请优先用这些样本解释“这个项目服务谁、对标谁的痛点、靠哪些框架能力落地”。\n");
        builder.append("</verified_case_signal_summary>\n\n");
        return builder.toString();
    }

    public String renderEvaluationGroupingSummary() {
        StringBuilder builder = new StringBuilder();
        builder.append("\n## Verified Case Grouping Snapshot\n\n");
        appendGroupingSection(builder, "By Target Role", cases.stream()
                .collect(Collectors.groupingBy(VerifiedCaseDescriptor::targetRole, LinkedHashMap::new, Collectors.toList())));
        appendGroupingSection(builder, "By Benchmark Reference", cases.stream()
                .collect(Collectors.groupingBy(VerifiedCaseDescriptor::benchmarkReference, LinkedHashMap::new, Collectors.toList())));
        appendGroupingSection(builder, "By Framework Capability", buildCapabilityGrouping());
        return builder.toString();
    }

    private void appendGroupingSection(StringBuilder builder, String title, Map<String, List<VerifiedCaseDescriptor>> groups) {
        builder.append("### ").append(title).append("\n\n");
        for (Map.Entry<String, List<VerifiedCaseDescriptor>> entry : groups.entrySet()) {
            builder.append("- ").append(entry.getKey())
                    .append(" (").append(entry.getValue().size()).append(")\n");
            for (VerifiedCaseDescriptor descriptor : entry.getValue()) {
                builder.append("  - ").append(descriptor.id())
                        .append(" / ").append(descriptor.analysisSpace())
                        .append(" / ").append(descriptor.pathType())
                        .append("\n");
            }
        }
        builder.append("\n");
    }

    private Map<String, List<VerifiedCaseDescriptor>> buildCapabilityGrouping() {
        Map<String, List<VerifiedCaseDescriptor>> grouping = new LinkedHashMap<>();
        for (VerifiedCaseDescriptor descriptor : cases) {
            for (String capability : descriptor.frameworkCapabilities()) {
                grouping.computeIfAbsent(capability, ignored -> new ArrayList<>()).add(descriptor);
            }
        }
        return grouping;
    }

    @SuppressWarnings("unchecked")
    private List<VerifiedCaseDescriptor> loadCases(ResourceLoader resourceLoader) {
        Resource resource = resourceLoader.getResource("classpath:experiences/verified_cases.yaml");
        YamlMapFactoryBean yamlMapFactoryBean = new YamlMapFactoryBean();
        yamlMapFactoryBean.setResources(resource);
        Map<String, Object> root = yamlMapFactoryBean.getObject();
        if (root == null || !(root.get("cases") instanceof List<?> rawCases)) {
            return List.of();
        }
        List<VerifiedCaseDescriptor> result = new ArrayList<>();
        for (Object rawCase : rawCases) {
            if (!(rawCase instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> caseMap = (Map<String, Object>) rawMap;
            result.add(new VerifiedCaseDescriptor(
                    readString(caseMap, "id"),
                    readString(caseMap, "analysis_space"),
                    readString(caseMap, "path_type"),
                    readString(caseMap, "target_role"),
                    readString(caseMap, "benchmark_reference"),
                    readStringList(caseMap, "framework_capabilities"),
                    readString(caseMap, "user_question")
            ));
        }
        return result;
    }

    private int score(VerifiedCaseDescriptor descriptor, String query) {
        int score = 0;
        score += scoreText(descriptor.targetRole(), query, 4, 2);
        score += scoreText(descriptor.benchmarkReference(), query, 5, 2);
        score += scoreText(descriptor.userQuestion(), query, 1, 1);
        for (String token : tokenize(descriptor.targetRole())) {
            if (query.contains(token)) {
                score += 4;
            }
        }
        for (String token : tokenize(descriptor.benchmarkReference())) {
            if (query.contains(token)) {
                score += 5;
            }
        }
        for (String capability : descriptor.frameworkCapabilities()) {
            String normalizedCapability = normalize(capability);
            if (!normalizedCapability.isBlank() && query.contains(normalizedCapability)) {
                score += 4;
            }
            for (String token : tokenize(capability)) {
                if (query.contains(token)) {
                    score += 3;
                }
            }
        }
        for (String token : tokenize(descriptor.userQuestion())) {
            if (query.contains(token)) {
                score += 1;
            }
        }
        return score;
    }

    private int scoreText(String source, String query, int fullMatchWeight, int tokenMatchWeight) {
        String normalizedSource = normalize(source);
        if (normalizedSource.isBlank()) {
            return 0;
        }
        int score = 0;
        if (query.contains(normalizedSource) || normalizedSource.contains(query)) {
            score += fullMatchWeight;
        }
        for (String keyword : List.of("阿里", "京东", "拼多多", "运营", "分析师", "售后", "活动", "roi", "experience", "fastintent", "tool")) {
            if (normalizedSource.contains(normalize(keyword)) && query.contains(normalize(keyword))) {
                score += tokenMatchWeight;
            }
        }
        return score;
    }

    private List<String> tokenize(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("[\\s/、,，：:（）()]+")).stream()
                .map(this::normalize)
                .filter(token -> token.length() >= 2)
                .distinct()
                .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private String readString(Map<String, Object> source, String key) {
        return Objects.toString(source.get(key), "");
    }

    @SuppressWarnings("unchecked")
    private List<String> readStringList(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (!(value instanceof List<?> rawList)) {
            return List.of();
        }
        return rawList.stream().map(item -> Objects.toString(item, "")).toList();
    }

    public record VerifiedCaseDescriptor(
            String id,
            String analysisSpace,
            String pathType,
            String targetRole,
            String benchmarkReference,
            List<String> frameworkCapabilities,
            String userQuestion
    ) {
    }

    private record ScoredVerifiedCase(VerifiedCaseDescriptor descriptor, int score) {
    }
}
