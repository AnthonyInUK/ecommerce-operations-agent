package com.alibaba.assistant.agent.start.config;

import com.alibaba.assistant.agent.extension.experience.model.DisclosureStrategy;
import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceArtifact;
import com.alibaba.assistant.agent.extension.experience.model.FastIntentConfig;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceMetadata;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.experience.model.ReferenceEntry;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Order(90)
@ConditionalOnProperty(
        prefix = "assistant.agent.start.ecommerce-experiences",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class EcommerceExperienceBootstrapConfig implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EcommerceExperienceBootstrapConfig.class);

    private final ExperienceRepository experienceRepository;
    private final ResourceLoader resourceLoader;
    private final VerifiedCaseCatalog verifiedCaseCatalog;

    public EcommerceExperienceBootstrapConfig(
            ExperienceRepository experienceRepository,
            ResourceLoader resourceLoader,
            VerifiedCaseCatalog verifiedCaseCatalog) {
        this.experienceRepository = experienceRepository;
        this.resourceLoader = resourceLoader;
        this.verifiedCaseCatalog = verifiedCaseCatalog;
    }

    @Override
    public void run(String... args) {
        List<Experience> experiences = List.of(
                buildProgressiveExperience(
                        "exp-ecom-metric-dictionary",
                        ExperienceType.COMMON,
                        "电商指标词典",
                        "固定 GMV、DAU、退款率、ROI 等核心业务口径，避免业务和 Agent 对同一个词理解不一致。",
                        """
                                这是电商内部运营分析 Agent 的指标词典入口。
                                当问题涉及 GMV、活跃用户、转化率、退款率、ROI 等核心指标时，应先遵循这里定义的业务口径、
                                默认过滤条件和允许拆分维度，而不是临时猜测“这个词大概是什么意思”。
                                """,
                        Set.of("指标词典", "口径", "GMV", "DAU", "退款率"),
                        List.of(reference("experiences/metric_dictionary.yaml", "application/yaml", "电商指标词典原始定义"))
                ),
                buildProgressiveExperience(
                        "exp-ecom-semantic-model",
                        ExperienceType.COMMON,
                        "电商分析语义模型",
                        "把大盘、增长、退款、活动四个分析空间拆开，明确每类问题应该优先看哪些事实表和维度。",
                        """
                                这是电商分析 Agent 的轻量语义模型。
                                它定义了不同分析空间的业务目标、主事实表、支持指标、维度和快路径/深路径边界，
                                用来帮助 Agent 判断“这个问题属于哪一类业务分析”。
                                """,
                        Set.of("semantic model", "analysis space", "大盘", "增长", "退款", "活动"),
                        List.of(reference("experiences/semantic_model.yaml", "application/yaml", "电商分析语义模型"))
                ),
                buildDirectExperience(
                        "exp-ecom-business-capability-map",
                        ExperienceType.COMMON,
                        "业务痛点到框架能力映射",
                        "把阿里、京东、拼多多式的数据分析痛点，映射到当前 Agent 的框架能力和项目资产上。",
                        """
                                这是产品定位、业务价值和竞品差异问答的优先经验。
                                当用户问“这个 Agent 是什么产品”“解决什么业务问题”“和阿里/京东/拼多多现有做法有什么差异”时，
                                优先从三条主线回答：
                                1. 阿里式痛点：工具很多，但业务到分析结论链路仍然慢；
                                2. 京东式痛点：标准问数强，但复杂归因仍需要多步分析；
                                3. 拼多多式痛点：重复分析多，人效压力高，自动化不足。
                                我们的项目对应的是：把查数、下钻、归因、报告、预警产品化成框架内可复用能力。
                                """,
                        Set.of("业务映射", "阿里", "京东", "拼多多", "框架能力", "产品定位", "业务价值", "竞品差异"),
                        List.of(reference("experiences/business_capability_map.md", "text/markdown", "业务痛点与框架能力映射"))
                ),
                buildProgressiveExperience(
                        "exp-ecom-verified-cases",
                        ExperienceType.REACT,
                        "电商已验证问题样本",
                        "沉淀高频标准问题和复杂归因问题的标准分析链，作为快路径和评测的共同基线。",
                        """
                                这是电商场景 verified cases 的入口。
                                当用户问题与这些高频样本高度相似时，Agent 应优先复用已经验证过的分析链，
                                而不是每次都从零规划，以提高响应速度、一致性和可信度。
                                """,
                        Set.of("verified cases", "高频问题", "归因", "快路径", "深路径"),
                        List.of(reference("experiences/verified_cases.yaml", "application/yaml", "电商 verified cases 基线"))
                ),
                buildProgressiveExperience(
                        "exp-ecom-report-templates",
                        ExperienceType.REACT,
                        "日报周报与异常通知模板",
                        "统一日报、周报和退款风险提醒的输出结构，让 Agent 结果更像真实业务材料。",
                        """
                                这是面向运营、分析师和售后团队的标准输出模板。
                                当 Agent 需要生成日报、周报、异常提醒或复盘材料时，应优先按这些模板组织内容，
                                让输出结果更像可直接发给团队的业务文档，而不是只给一段零散结论。
                                """,
                        Set.of("日报", "周报", "异常预警", "报告模板"),
                        List.of(reference("experiences/report_templates.yaml", "application/yaml", "日报周报模板"))
                ),
                buildProgressiveExperience(
                        "exp-ecom-evaluation-loop",
                        ExperienceType.COMMON,
                        "评测与失败回流闭环",
                        "记录 bad cases、评测指标和系统改进方向，确保 Agent 不是一次性答题工具，而是会持续变强的系统。",
                        """
                                这是系统自我改进的业务闭环入口。
                                好问题需要沉淀成 verified cases 和 Experience，失败问题需要进入 bad case 池，
                                再通过语义模型修正、模板补充和评测汇总推动系统变强。
                                """,
                        Set.of("bad case", "评测", "自演进", "经验沉淀"),
                        List.of(
                                reference("experiences/bad_cases.yaml", "application/yaml", "典型失败问题池"),
                                evaluationSummaryReference()
                        )
                ),
                buildFastIntentReactExperience(
                        "exp-ecom-fast-gmv-yesterday",
                        "昨天 GMV 快速看数",
                        "命中“昨天 GMV 多少”这类高频看数问题时，直接查询昨日大盘指标。",
                        "这是标准高频看数问题，直接查询昨日大盘指标并返回给后续模型总结。",
                        ".*昨天\\s*GMV.*多少.*",
                        List.of(toolCall("GmvQueryTool", Map.of("stat_date", "2026-05-17"))),
                        List.of("GmvQueryTool"),
                        List.of(reference("experiences/verified_cases.yaml", "application/yaml", "昨日 GMV verified case"))
                ),
                buildFastIntentReactExperience(
                        "exp-ecom-fast-region-compare",
                        "区域对比快路径",
                        "命中“华东和华南哪个更差”这类区域对比问题时，直接查询区域表现。",
                        "这是标准区域对比问题，直接查询指定日期的区域表现并返回给后续模型总结。",
                        ".*(华东.*华南|华南.*华东).*(哪个|谁).*(更差|差|表现).*",
                        List.of(toolCall("RegionPerformanceQueryTool", Map.of("stat_date", "2026-05-17"))),
                        List.of("RegionPerformanceQueryTool"),
                        List.of(reference("experiences/verified_cases.yaml", "application/yaml", "区域对比 verified case"))
                ),
                buildFastIntentReactExperience(
                        "exp-ecom-fast-category-rank",
                        "品类排行快路径",
                        "命中“品类排行/排名/TOP”这类高频结构问题时，直接查询品类排行。",
                        "这是标准品类结构问题，直接查询指定日期的品类排行并返回给后续模型总结。",
                        ".*品类.*(排行|排名|TOP|top).*",
                        List.of(toolCall("CategoryRankTool", Map.of("stat_date", "2026-05-17", "limit", 3))),
                        List.of("CategoryRankTool"),
                        List.of(reference("experiences/verified_cases.yaml", "application/yaml", "品类排行 verified case"))
                ),
                buildFastIntentReactExperience(
                        "exp-ecom-fast-user-overview",
                        "用户规模快路径",
                        "命中“昨天 DAU/活跃买家多少”这类高频用户规模问题时，直接查询用户增长概览。",
                        "这是标准用户规模看数问题，直接查询指定日期的 DAU、活跃买家数和买家激活率并返回给后续模型总结。",
                        ".*(DAU|活跃买家|活跃用户).*(多少|怎么样|如何).*",
                        List.of(toolCall("UserMetricTool", Map.of("stat_date", "2026-05-17"))),
                        List.of("UserMetricTool"),
                        List.of(reference("experiences/verified_cases.yaml", "application/yaml", "昨日用户规模 verified case"))
                ),
                buildFastIntentReactExperience(
                        "exp-ecom-fast-channel-conversion",
                        "渠道转化快路径",
                        "命中“哪个渠道转化更高”这类高频渠道质量问题时，直接查询渠道转化表现。",
                        "这是标准渠道质量问题，直接查询指定日期的渠道转化率排行并返回给后续模型总结。",
                        ".*渠道.*转化.*(更高|最高|怎么样|如何|排行|排名)?.*",
                        List.of(toolCall("UserMetricTool", Map.of("stat_date", "2026-05-17", "view_type", "channel", "limit", 3))),
                        List.of("UserMetricTool"),
                        List.of(reference("experiences/verified_cases.yaml", "application/yaml", "渠道转化 verified case"))
                ),
                buildFastIntentReactExperience(
                        "exp-ecom-fast-root-cause-east-drop",
                        "华东 GMV 下跌归因快深结合",
                        "命中“华东 GMV 为什么跌了”这类标准归因问题时，直接触发固定分析链。",
                        "这是标准区域归因问题，按固定套路依次执行区域、订单结构、用户规模、品类、漏斗和退款分析工具。",
                        ".*华东.*GMV.*为什么跌.*",
                        List.of(
                                toolCall("RegionPerformanceQueryTool", Map.of("stat_date", "2026-05-17", "region_name", "华东")),
                                toolCall("RegionPerformanceQueryTool", Map.of("stat_date", "2026-05-16", "region_name", "华东")),
                                toolCall("OrderQueryTool", Map.of("stat_date", "2026-05-17", "region_name", "华东")),
                                toolCall("OrderQueryTool", Map.of("stat_date", "2026-05-16", "region_name", "华东")),
                                toolCall("UserMetricTool", Map.of("stat_date", "2026-05-17", "region_name", "华东")),
                                toolCall("UserMetricTool", Map.of("stat_date", "2026-05-16", "region_name", "华东")),
                                toolCall("CategoryRankTool", Map.of("stat_date", "2026-05-17", "region_name", "华东", "limit", 5)),
                                toolCall("CategoryRankTool", Map.of("stat_date", "2026-05-16", "region_name", "华东", "limit", 5)),
                                toolCall("FunnelAnalysisTool", Map.of("stat_date", "2026-05-17", "region_name", "华东")),
                                toolCall("FunnelAnalysisTool", Map.of("stat_date", "2026-05-16", "region_name", "华东")),
                                toolCall("RefundAnalysisTool", Map.of("stat_date", "2026-05-17", "region_name", "华东", "limit", 3))
                        ),
                        List.of("RegionPerformanceQueryTool", "OrderQueryTool", "UserMetricTool", "CategoryRankTool", "FunnelAnalysisTool", "RefundAnalysisTool"),
                        List.of(
                                reference("experiences/verified_cases.yaml", "application/yaml", "华东 GMV 下跌归因 verified case"),
                                reference("experiences/report_templates.yaml", "application/yaml", "周度异常归因模板")
                        )
                )
        );
        experienceRepository.batchSave(experiences);
        log.info("EcommerceExperienceBootstrapConfig#run - reason=loaded ecommerce experiences count={}", experiences.size());
    }

    private Experience buildProgressiveExperience(
            String id,
            ExperienceType type,
            String name,
            String description,
            String content,
            Set<String> tags,
            List<ReferenceEntry> references
    ) {
        Experience experience = new Experience();
        experience.setId(id);
        experience.setType(type);
        experience.setName(name);
        experience.setDescription(description);
        experience.setContent(content.strip());
        experience.setTags(tags);
        experience.setDisclosureStrategy(DisclosureStrategy.PROGRESSIVE);
        experience.setCreatedAt(Instant.now());
        experience.setUpdatedAt(Instant.now());
        experience.setReferences(references);

        ExperienceMetadata metadata = new ExperienceMetadata();
        metadata.setSource("classpath:experiences");
        metadata.setVersion("v1");
        metadata.setConfidence(0.95);
        metadata.setProperties(Map.of("domain", "ecommerce-analytics"));
        experience.setMetadata(metadata);
        return experience;
    }

    private Experience buildDirectExperience(
            String id,
            ExperienceType type,
            String name,
            String description,
            String content,
            Set<String> tags,
            List<ReferenceEntry> references
    ) {
        Experience experience = buildProgressiveExperience(id, type, name, description, content, tags, references);
        experience.setDisclosureStrategy(DisclosureStrategy.DIRECT);
        if (experience.getMetadata() != null) {
            experience.getMetadata().setConfidence(0.99);
        }
        return experience;
    }

    private Experience buildFastIntentReactExperience(
            String id,
            String name,
            String description,
            String assistantText,
            String regexPattern,
            List<ExperienceArtifact.ToolCallSpec> toolCalls,
            List<String> associatedTools,
            List<ReferenceEntry> references
    ) {
        Experience experience = new Experience();
        experience.setId(id);
        experience.setType(ExperienceType.REACT);
        experience.setName(name);
        experience.setDescription(description);
        experience.setContent(assistantText);
        experience.setDisclosureStrategy(DisclosureStrategy.PROGRESSIVE);
        experience.setCreatedAt(Instant.now());
        experience.setUpdatedAt(Instant.now());
        experience.setTags(Set.of("fast-intent", "react", "ecommerce", "verified-case"));
        experience.setAssociatedTools(associatedTools);
        experience.setReferences(references);

        ExperienceArtifact artifact = new ExperienceArtifact();
        ExperienceArtifact.ReactArtifact reactArtifact = new ExperienceArtifact.ReactArtifact();
        reactArtifact.setAssistantText(assistantText);
        ExperienceArtifact.ToolPlan toolPlan = new ExperienceArtifact.ToolPlan();
        toolPlan.setToolCalls(toolCalls);
        reactArtifact.setPlan(toolPlan);
        artifact.setReact(reactArtifact);
        experience.setArtifact(artifact);

        FastIntentConfig fastIntentConfig = new FastIntentConfig();
        fastIntentConfig.setEnabled(true);
        fastIntentConfig.setPriority(toolCalls.size() > 1 ? 90 : 100);
        FastIntentConfig.MatchExpression expression = new FastIntentConfig.MatchExpression();
        FastIntentConfig.Condition condition = new FastIntentConfig.Condition();
        condition.setType("message_regex");
        condition.setPattern(regexPattern);
        condition.setIgnoreCase(true);
        condition.setTrim(true);
        expression.setCondition(condition);
        fastIntentConfig.setMatch(expression);
        fastIntentConfig.getOnMatch().setMode(FastIntentConfig.FastIntentMode.FASTPATH_THEN_REFERENCE);
        fastIntentConfig.getOnMatch().setFallback(FastIntentConfig.FastIntentFallback.REFERENCE_ONLY);
        experience.setFastIntentConfig(fastIntentConfig);

        ExperienceMetadata metadata = new ExperienceMetadata();
        metadata.setSource("classpath:experiences");
        metadata.setVersion("v1");
        metadata.setConfidence(0.98);
        metadata.setProperties(Map.of(
                "domain", "ecommerce-analytics",
                "fastIntent", true,
                "pathType", toolCalls.size() > 1 ? "deep" : "fast"
        ));
        experience.setMetadata(metadata);
        return experience;
    }

    private ExperienceArtifact.ToolCallSpec toolCall(String toolName, Map<String, Object> arguments) {
        ExperienceArtifact.ToolCallSpec toolCallSpec = new ExperienceArtifact.ToolCallSpec();
        toolCallSpec.setToolName(toolName);
        toolCallSpec.setArguments(arguments);
        return toolCallSpec;
    }

    private ReferenceEntry reference(String classpathPath, String mediaType, String description) {
        Resource resource = resourceLoader.getResource("classpath:" + classpathPath);
        try {
            byte[] bytes = readAllBytes(resource);
            ReferenceEntry entry = new ReferenceEntry();
            entry.setPath(classpathPath);
            entry.setMediaType(mediaType);
            entry.setDescription(description);
            entry.setContent(new String(bytes, StandardCharsets.UTF_8));
            entry.setSize((long) bytes.length);
            entry.setContentHash(Integer.toHexString(entry.getContent().hashCode()));
            return entry;
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to load experience resource: " + classpathPath, ex);
        }
    }

    private ReferenceEntry evaluationSummaryReference() {
        ReferenceEntry entry = reference("experiences/evaluation_summary.md", "text/markdown", "评测汇总与周报");
        String enrichedContent = entry.getContent() + verifiedCaseCatalog.renderEvaluationGroupingSummary();
        entry.setContent(enrichedContent);
        entry.setSize((long) enrichedContent.getBytes(StandardCharsets.UTF_8).length);
        entry.setContentHash(Integer.toHexString(enrichedContent.hashCode()));
        return entry;
    }

    private byte[] readAllBytes(Resource resource) throws IOException {
        try (InputStream inputStream = resource.getInputStream()) {
            return inputStream.readAllBytes();
        }
    }
}
