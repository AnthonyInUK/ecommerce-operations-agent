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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class MetricDictionaryCatalog {

    private static final Logger log = LoggerFactory.getLogger(MetricDictionaryCatalog.class);

    private final Map<String, MetricEntry> metricsById;

    public MetricDictionaryCatalog() {
        this.metricsById = loadMetricDictionary();
    }

    public boolean matches(String metricId, String question) {
        MetricEntry entry = metricsById.get(metricId);
        if (entry == null || question == null || question.isBlank()) {
            return false;
        }
        String normalizedQuestion = normalize(question);
        if (normalizedQuestion.contains(normalize(entry.name()))) {
            return true;
        }
        return entry.aliases().stream().map(this::normalize).anyMatch(normalizedQuestion::contains);
    }

    public String resolveDisambiguationQuestion(String question) {
        for (MetricEntry entry : metricsById.values()) {
            if (entry.disambiguationRequired() && matches(entry.id(), question)) {
                return entry.disambiguationQuestion();
            }
        }
        return null;
    }

    public Set<String> metricIds() {
        return metricsById.keySet();
    }

    private Map<String, MetricEntry> loadMetricDictionary() {
        try (InputStream inputStream = new ClassPathResource("experiences/metric_dictionary.yaml").getInputStream()) {
            String yamlText = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> root = new Yaml().load(yamlText);
            Object metrics = root == null ? null : root.get("metrics");
            if (!(metrics instanceof List<?> metricList)) {
                return Map.of();
            }

            Map<String, MetricEntry> entries = new LinkedHashMap<>();
            for (Object item : metricList) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                String id = stringValue(raw.get("id"));
                String name = stringValue(raw.get("name"));
                if (id == null || name == null) {
                    continue;
                }
                List<String> aliases = stringList(raw.get("aliases"));
                boolean disambiguationRequired = Boolean.TRUE.equals(raw.get("disambiguation_required"));
                String disambiguationQuestion = stringValue(raw.get("disambiguation_question"));
                entries.put(id, new MetricEntry(id, name, aliases, disambiguationRequired, disambiguationQuestion));
            }
            log.info("MetricDictionaryCatalog#loadMetricDictionary - reason=metric dictionary loaded, metricCount={}", entries.size());
            return entries;
        }
        catch (Exception ex) {
            log.warn("MetricDictionaryCatalog#loadMetricDictionary - reason=failed to load metric dictionary, message={}", ex.getMessage());
            return Map.of();
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "").toLowerCase();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return Collections.emptyList();
        }
        List<String> values = new ArrayList<>();
        for (Object item : list) {
            if (item != null) {
                values.add(String.valueOf(item));
            }
        }
        return values;
    }

    public record MetricEntry(String id,
                              String name,
                              List<String> aliases,
                              boolean disambiguationRequired,
                              String disambiguationQuestion) {
        public MetricEntry {
            aliases = Objects.requireNonNullElse(aliases, List.of());
        }
    }
}
