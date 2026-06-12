package com.alibaba.assistant.agent.start.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class SemanticModelCatalog {

    private static final Logger log = LoggerFactory.getLogger(SemanticModelCatalog.class);

    private final Set<String> regions;
    private final Set<String> categories;
    private final List<ConversationFollowUpRule> followUpRules;

    public SemanticModelCatalog() {
        SemanticModelData data = loadSemanticModel();
        this.regions = data.regions();
        this.categories = data.categories();
        this.followUpRules = data.followUpRules();
    }

    public Set<String> regions() {
        return regions;
    }

    public Set<String> categories() {
        return categories;
    }

    public boolean isRefundFollowUp(String question) {
        return matchesFollowUpRule(question, "退款率");
    }

    public boolean isOrderStructureFollowUp(String question) {
        return matchesFollowUpRule(question, "订单量") || matchesFollowUpRule(question, "客单价");
    }

    public boolean isUserScaleFollowUp(String question) {
        return matchesFollowUpRule(question, "用户") || matchesFollowUpRule(question, "活跃买家");
    }

    private boolean matchesFollowUpRule(String question, String keyword) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String normalizedQuestion = normalize(question);
        return followUpRules.stream()
                .map(ConversationFollowUpRule::pattern)
                .map(this::normalize)
                .anyMatch(pattern -> pattern.contains(normalize(keyword)) && normalizedQuestion.contains(normalize(keyword)));
    }

    private SemanticModelData loadSemanticModel() {
        try (InputStream inputStream = new ClassPathResource("experiences/semantic_model.yaml").getInputStream()) {
            String yamlText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> root = new Yaml().load(yamlText);

            Set<String> loadedRegions = new LinkedHashSet<>();
            Set<String> loadedCategories = new LinkedHashSet<>();
            List<ConversationFollowUpRule> loadedFollowUpRules = new ArrayList<>();

            Object dimensionMappings = root == null ? null : root.get("dimension_mappings");
            if (dimensionMappings instanceof Map<?, ?> dimensions) {
                Object region = dimensions.get("region");
                if (region instanceof Map<?, ?> regionMap) {
                    Object values = regionMap.get("values");
                    if (values instanceof List<?> regionValues) {
                        for (Object item : regionValues) {
                            if (item instanceof Map<?, ?> raw) {
                                String name = stringValue(raw.get("name"));
                                if (name != null) {
                                    loadedRegions.add(name);
                                }
                            }
                        }
                    }
                }

                Object category = dimensions.get("category_l1");
                if (category instanceof Map<?, ?> categoryMap) {
                    Object examples = categoryMap.get("examples");
                    if (examples instanceof List<?> categoryExamples) {
                        for (Object item : categoryExamples) {
                            if (item != null) {
                                loadedCategories.add(String.valueOf(item));
                            }
                        }
                    }
                }
            }

            Object conversationRules = root == null ? null : root.get("conversation_follow_up_rules");
            if (conversationRules instanceof List<?> rules) {
                for (Object item : rules) {
                    if (!(item instanceof Map<?, ?> raw)) {
                        continue;
                    }
                    String pattern = stringValue(raw.get("pattern"));
                    String interpretation = stringValue(raw.get("interpretation"));
                    if (pattern != null) {
                        loadedFollowUpRules.add(new ConversationFollowUpRule(pattern, interpretation));
                    }
                }
            }

            log.info("SemanticModelCatalog#loadSemanticModel - reason=semantic model loaded, regionCount={}, categoryCount={}, followUpRuleCount={}",
                    loadedRegions.size(), loadedCategories.size(), loadedFollowUpRules.size());
            return new SemanticModelData(
                    Collections.unmodifiableSet(loadedRegions),
                    Collections.unmodifiableSet(loadedCategories),
                    List.copyOf(loadedFollowUpRules)
            );
        }
        catch (Exception ex) {
            log.warn("SemanticModelCatalog#loadSemanticModel - reason=failed to load semantic model, message={}", ex.getMessage());
            return new SemanticModelData(Set.of(), Set.of(), List.of());
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").toLowerCase();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public record ConversationFollowUpRule(String pattern, String interpretation) {
        public ConversationFollowUpRule {
            interpretation = Objects.requireNonNullElse(interpretation, "");
        }
    }

    private record SemanticModelData(Set<String> regions,
                                     Set<String> categories,
                                     List<ConversationFollowUpRule> followUpRules) {
    }
}
