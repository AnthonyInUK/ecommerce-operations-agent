package com.alibaba.assistant.agent.start.tool;

import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.assistant.agent.common.tools.CodeExample;
import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.common.tools.CodeactToolMetadata;
import com.alibaba.assistant.agent.common.tools.DefaultCodeactToolMetadata;
import com.alibaba.assistant.agent.common.tools.definition.CodeactToolDefinition;
import com.alibaba.assistant.agent.common.tools.definition.DefaultCodeactToolDefinition;
import com.alibaba.assistant.agent.common.tools.definition.ParameterNode;
import com.alibaba.assistant.agent.common.tools.definition.ParameterTree;
import com.alibaba.assistant.agent.common.tools.definition.ParameterType;
import com.alibaba.assistant.agent.start.observability.LogAround;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Shared base class for the first batch of ecommerce warehouse query tools.
 * It keeps JSON parsing and metadata building consistent so each tool only needs
 * to express one business action.
 */
public abstract class AbstractWarehouseQueryCodeactTool implements CodeactTool {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ToolDefinition toolDefinition;
    private final CodeactToolDefinition codeactDefinition;
    private final CodeactToolMetadata codeactMetadata;

    protected AbstractWarehouseQueryCodeactTool(String toolName,
                                                String description,
                                                List<ParameterNode> parameters,
                                                String targetClassName,
                                                String targetClassDescription,
                                                CodeExample fewShot) {
        String inputSchema = buildInputSchema(parameters);
        this.toolDefinition = ToolDefinition.builder()
                .name(toolName)
                .description(description)
                .inputSchema(inputSchema)
                .build();
        this.codeactDefinition = buildCodeactDefinition(toolName, description, inputSchema, parameters);
        this.codeactMetadata = DefaultCodeactToolMetadata.builder()
                .addSupportedLanguage(Language.PYTHON)
                .targetClassName(targetClassName)
                .targetClassDescription(targetClassDescription)
                .addFewShot(fewShot)
                .displayName(toolName)
                .returnDirect(false)
                .build();
    }

    @Override
    @LogAround
    public String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    @LogAround
    public String call(String toolInput, ToolContext toolContext) {
        try {
            Map<String, Object> params = toolInput == null || toolInput.isBlank()
                    ? Map.of()
                    : objectMapper.readValue(toolInput, Map.class);
            Object result = execute(params, toolContext);
            return objectMapper.writeValueAsString(result);
        }
        catch (Exception ex) {
            try {
                return objectMapper.writeValueAsString(Map.of(
                        "success", false,
                        "message", ex.getMessage()
                ));
            }
            catch (Exception ignored) {
                return "{\"success\":false,\"message\":\"tool execution failed\"}";
            }
        }
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public CodeactToolDefinition getCodeactDefinition() {
        return codeactDefinition;
    }

    @Override
    public CodeactToolMetadata getCodeactMetadata() {
        return codeactMetadata;
    }

    protected LocalDate resolveDate(Map<String, Object> params) {
        Object raw = params.get("stat_date");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return LocalDate.of(2026, 5, 17);
        }
        return LocalDate.parse(String.valueOf(raw));
    }

    protected Integer resolveLimit(Map<String, Object> params, int defaultLimit) {
        Object raw = params.get("limit");
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw != null) {
            return Integer.parseInt(String.valueOf(raw));
        }
        return defaultLimit;
    }

    protected String resolveOptionalString(Map<String, Object> params, String key) {
        Object raw = params.get(key);
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        return value.isEmpty() ? null : value;
    }

    protected abstract Object execute(Map<String, Object> params, ToolContext toolContext);

    private String buildInputSchema(List<ParameterNode> parameters) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"type\":\"object\",\"properties\":{");
        for (int i = 0; i < parameters.size(); i++) {
            ParameterNode parameter = parameters.get(i);
            if (i > 0) {
                builder.append(",");
            }
            builder.append("\"").append(parameter.getName()).append("\":{")
                    .append("\"type\":\"").append(parameter.getType().getJsonSchemaType()).append("\"");
            if (parameter.getDescription() != null && !parameter.getDescription().isBlank()) {
                builder.append(",\"description\":\"")
                        .append(parameter.getDescription().replace("\"", "\\\""))
                        .append("\"");
            }
            builder.append("}");
        }
        builder.append("}");

        List<String> requiredNames = parameters.stream()
                .filter(ParameterNode::isRequired)
                .map(ParameterNode::getName)
                .toList();
        if (!requiredNames.isEmpty()) {
            builder.append(",\"required\":[");
            for (int i = 0; i < requiredNames.size(); i++) {
                if (i > 0) {
                    builder.append(",");
                }
                builder.append("\"").append(requiredNames.get(i)).append("\"");
            }
            builder.append("]");
        }

        builder.append("}");
        return builder.toString();
    }

    private CodeactToolDefinition buildCodeactDefinition(String toolName,
                                                         String description,
                                                         String inputSchema,
                                                         List<ParameterNode> parameters) {
        ParameterTree.Builder parameterTreeBuilder = ParameterTree.builder().rawInputSchema(inputSchema);
        for (ParameterNode parameter : parameters) {
            parameterTreeBuilder.addParameter(parameter);
        }
        return DefaultCodeactToolDefinition.builder()
                .name(toolName)
                .description(description)
                .inputSchema(inputSchema)
                .parameterTree(parameterTreeBuilder.build())
                .returnDescription("业务查询结果")
                .returnTypeHint("Dict[str, Any]")
                .build();
    }

    protected static ParameterNode requiredString(String name, String description) {
        return ParameterNode.builder()
                .name(name)
                .type(ParameterType.STRING)
                .description(description)
                .required(true)
                .build();
    }

    protected static ParameterNode optionalString(String name, String description) {
        return ParameterNode.builder()
                .name(name)
                .type(ParameterType.STRING)
                .description(description)
                .required(false)
                .build();
    }

    protected static ParameterNode optionalInteger(String name, String description, int defaultValue) {
        return ParameterNode.builder()
                .name(name)
                .type(ParameterType.INTEGER)
                .description(description)
                .required(false)
                .defaultValue(defaultValue)
                .build();
    }
}
