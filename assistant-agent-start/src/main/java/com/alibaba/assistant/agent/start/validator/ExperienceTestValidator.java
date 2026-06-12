package com.alibaba.assistant.agent.start.validator;

import com.alibaba.assistant.agent.extension.experience.fastintent.FastIntentContext;
import com.alibaba.assistant.agent.extension.experience.fastintent.FastIntentService;
import com.alibaba.assistant.agent.extension.experience.model.Experience;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQuery;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceQueryContext;
import com.alibaba.assistant.agent.extension.experience.model.ExperienceType;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider;
import com.alibaba.assistant.agent.extension.experience.spi.ExperienceRepository;
import com.alibaba.assistant.agent.start.config.JdbcWarehouseQueryService;
import com.alibaba.assistant.agent.start.config.VerifiedCaseCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Experience Module Test Validator
 *
 * <p>Tests experience query functionality after application startup to ensure
 * experience hooks are working correctly.
 * 
 * <p>验证 COMMON 和 REACT 类型的经验。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
@Order(1000) // Run after experience initialization
public class ExperienceTestValidator implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ExperienceTestValidator.class);

    private final ExperienceRepository experienceRepository;
    private final ExperienceProvider experienceProvider;
    private final JdbcWarehouseQueryService warehouseQueryService;
    private final FastIntentService fastIntentService;
    private final VerifiedCaseCatalog verifiedCaseCatalog;

    public ExperienceTestValidator(ExperienceRepository experienceRepository,
                                 ExperienceProvider experienceProvider,
                                 JdbcWarehouseQueryService warehouseQueryService,
                                 FastIntentService fastIntentService,
                                 VerifiedCaseCatalog verifiedCaseCatalog) {
        this.experienceRepository = experienceRepository;
        this.experienceProvider = experienceProvider;
        this.warehouseQueryService = warehouseQueryService;
        this.fastIntentService = fastIntentService;
        this.verifiedCaseCatalog = verifiedCaseCatalog;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("ExperienceTestValidator#run - reason=Starting experience module validation");

        testExperienceCount();
        testCommonExperienceQuery();
        testReactExperienceQuery();
        testVerifiedCasesReference();
        testBusinessCapabilityMapping();
        testBusinessCapabilityPriorityReady();
        testVerifiedCaseMetadataIntegration();
        testRuntimeBadCaseSnapshot();
        testWarehouseBootstrap();
        testFastIntentSelection();
        testRootCauseReactPlan();

        log.info("ExperienceTestValidator#run - reason=Experience module validation completed");
    }

    private void testExperienceCount() {
        long totalCount = experienceRepository.count();
        long reactCount = experienceRepository.countByType(ExperienceType.REACT);
        long commonCount = experienceRepository.countByType(ExperienceType.COMMON);

        log.info("ExperienceTestValidator#testExperienceCount - reason=Experience stats: total={}, react={}, common={}",
                totalCount, reactCount, commonCount);
    }

    private void testCommonExperienceQuery() {
        log.info("ExperienceTestValidator#testCommonExperienceQuery - reason=Testing common experience query");

        ExperienceQuery query = new ExperienceQuery(ExperienceType.COMMON);
        ExperienceQueryContext context = new ExperienceQueryContext();
        context.setTenantId("test-user");

        List<Experience> experiences = experienceProvider.query(query, context);

        log.info("ExperienceTestValidator#testCommonExperienceQuery - reason=Found {} common experiences", experiences.size());
        for (Experience exp : experiences) {
            log.debug("ExperienceTestValidator#testCommonExperienceQuery - reason=Common experience: title={}, tags={}",
                     exp.getTitle(), exp.getTags());
        }

        Set<String> expectedCommonTitles = Set.of("电商指标词典", "电商分析语义模型", "业务痛点到框架能力映射", "评测与失败回流闭环");
        Set<String> actualTitles = experiences.stream().map(Experience::getTitle).collect(java.util.stream.Collectors.toSet());
        boolean allPresent = actualTitles.containsAll(expectedCommonTitles);

        if (allPresent) {
            log.info("ExperienceTestValidator#testCommonExperienceQuery - reason=✅ ecommerce common experiences loaded titles={}", actualTitles);
        }
        else {
            log.warn("ExperienceTestValidator#testCommonExperienceQuery - reason=❌ ecommerce common experiences missing expectedTitles={}, actualTitles={}",
                    expectedCommonTitles, actualTitles);
        }
    }

    private void testReactExperienceQuery() {
        log.info("ExperienceTestValidator#testReactExperienceQuery - reason=Testing react experience query");

        ExperienceQuery query = new ExperienceQuery(ExperienceType.REACT);
        query.setRetrievalMode(ExperienceQuery.RetrievalMode.FULL_SCAN);
        query.setLimit(20);
        ExperienceQueryContext context = new ExperienceQueryContext();

        List<Experience> experiences = experienceProvider.query(query, context);

        log.info("ExperienceTestValidator#testReactExperienceQuery - reason=Found {} react experiences", experiences.size());

        Set<String> expectedReactTitles = Set.of(
                "电商已验证问题样本",
                "日报周报与异常通知模板",
                "昨天 GMV 快速看数",
                "区域对比快路径",
                "品类排行快路径",
                "用户规模快路径",
                "渠道转化快路径",
                "华东 GMV 下跌归因快深结合"
        );
        Set<String> actualTitles = experiences.stream().map(Experience::getTitle).collect(java.util.stream.Collectors.toSet());
        boolean allPresent = actualTitles.containsAll(expectedReactTitles);

        if (allPresent) {
            log.info("ExperienceTestValidator#testReactExperienceQuery - reason=✅ ecommerce react experiences loaded titles={}", actualTitles);
        }
        else {
            log.warn("ExperienceTestValidator#testReactExperienceQuery - reason=❌ ecommerce react experiences missing expectedTitles={}, actualTitles={}",
                    expectedReactTitles, actualTitles);
        }
    }

    private void testVerifiedCasesReference() {
        Experience experience = experienceRepository.findById("exp-ecom-verified-cases").orElse(null);
        if (experience == null) {
            log.warn("ExperienceTestValidator#testVerifiedCasesReference - reason=❌ verified cases experience missing");
            return;
        }

        long referenceCount = experience.getReferences().stream()
                .filter(reference -> "experiences/verified_cases.yaml".equals(reference.getPath()))
                .count();
        int caseCount = experience.getReferences().stream()
                .filter(reference -> "experiences/verified_cases.yaml".equals(reference.getPath()))
                .findFirst()
                .map(reference -> countOccurrences(reference.getContent(), "\n  - id:"))
                .orElse(0);

        if (referenceCount > 0 && caseCount >= 5) {
            log.info("ExperienceTestValidator#testVerifiedCasesReference - reason=✅ verified cases attached to experience, caseCount={}", caseCount);
        }
        else {
            log.warn("ExperienceTestValidator#testVerifiedCasesReference - reason=❌ verified cases reference invalid, referenceCount={}, caseCount={}",
                    referenceCount, caseCount);
        }
    }

    private void testBusinessCapabilityMapping() {
        Experience capabilityExperience = experienceRepository.findById("exp-ecom-business-capability-map").orElse(null);
        if (capabilityExperience == null) {
            log.warn("ExperienceTestValidator#testBusinessCapabilityMapping - reason=❌ business capability map experience missing");
            return;
        }

        String mappingContent = capabilityExperience.getReferences().stream()
                .filter(reference -> "experiences/business_capability_map.md".equals(reference.getPath()))
                .findFirst()
                .map(reference -> reference.getContent())
                .orElse("");

        Experience verifiedCasesExperience = experienceRepository.findById("exp-ecom-verified-cases").orElse(null);
        String verifiedCasesContent = verifiedCasesExperience == null ? "" : verifiedCasesExperience.getReferences().stream()
                .filter(reference -> "experiences/verified_cases.yaml".equals(reference.getPath()))
                .findFirst()
                .map(reference -> reference.getContent())
                .orElse("");

        boolean mappingHasBenchmarks = mappingContent.contains("阿里式")
                && mappingContent.contains("京东式")
                && mappingContent.contains("拼多多式");
        boolean verifiedCasesMapped = verifiedCasesContent.contains("benchmark_reference:")
                && countOccurrences(verifiedCasesContent, "framework_capabilities:") >= 5;

        if (mappingHasBenchmarks && verifiedCasesMapped) {
            log.info("ExperienceTestValidator#testBusinessCapabilityMapping - reason=✅ benchmark pain mapping linked to framework capabilities and verified cases");
        }
        else {
            log.warn("ExperienceTestValidator#testBusinessCapabilityMapping - reason=❌ benchmark pain mapping incomplete, mappingHasBenchmarks={}, verifiedCasesMapped={}",
                    mappingHasBenchmarks, verifiedCasesMapped);
        }
    }

    private void testBusinessCapabilityPriorityReady() {
        Experience capabilityExperience = experienceRepository.findById("exp-ecom-business-capability-map").orElse(null);
        if (capabilityExperience == null) {
            log.warn("ExperienceTestValidator#testBusinessCapabilityPriorityReady - reason=❌ business capability map experience missing");
            return;
        }

        boolean directReady = capabilityExperience.getDisclosureStrategy() != null
                && "DIRECT".equals(capabilityExperience.getDisclosureStrategy().name())
                && capabilityExperience.getContent() != null
                && capabilityExperience.getContent().length() <= 500;

        if (directReady) {
            log.info("ExperienceTestValidator#testBusinessCapabilityPriorityReady - reason=✅ business capability map ready for direct disclosure, contentLength={}",
                    capabilityExperience.getContent().length());
        }
        else {
            log.warn("ExperienceTestValidator#testBusinessCapabilityPriorityReady - reason=❌ business capability map not direct-disclosure ready, strategy={}, contentLength={}",
                    capabilityExperience.getDisclosureStrategy(),
                    capabilityExperience.getContent() != null ? capabilityExperience.getContent().length() : -1);
        }
    }

    private void testVerifiedCaseMetadataIntegration() {
        List<VerifiedCaseCatalog.VerifiedCaseDescriptor> promptRelevantCases =
                verifiedCaseCatalog.findRelevantCases("这个 Agent 和京东路线有什么差异，框架能力怎么支撑？", 3);
        Experience evaluationLoop = experienceRepository.findById("exp-ecom-evaluation-loop").orElse(null);
        String evaluationSummaryContent = evaluationLoop == null ? "" : evaluationLoop.getReferences().stream()
                .filter(reference -> "experiences/evaluation_summary.md".equals(reference.getPath()))
                .findFirst()
                .map(reference -> reference.getContent())
                .orElse("");

        boolean promptContributionReady = promptRelevantCases.stream()
                .anyMatch(caseDescriptor -> caseDescriptor.benchmarkReference().contains("京东式"));
        boolean groupedSummaryReady = evaluationSummaryContent.contains("## Verified Case Grouping Snapshot")
                && evaluationSummaryContent.contains("### By Target Role")
                && evaluationSummaryContent.contains("### By Benchmark Reference")
                && evaluationSummaryContent.contains("### By Framework Capability")
                && evaluationSummaryContent.contains("### Runtime Bad Case Interpretation By Target Role")
                && evaluationSummaryContent.contains("### Runtime Bad Case Interpretation By Benchmark Reference")
                && evaluationSummaryContent.contains("### Runtime Bad Case Interpretation By Framework Capability");

        if (promptContributionReady && groupedSummaryReady) {
            log.info("ExperienceTestValidator#testVerifiedCaseMetadataIntegration - reason=✅ verified case metadata connected to prompt briefing and evaluation grouping");
        }
        else {
            log.warn("ExperienceTestValidator#testVerifiedCaseMetadataIntegration - reason=❌ verified case metadata integration incomplete, promptContributionReady={}, groupedSummaryReady={}",
                    promptContributionReady, groupedSummaryReady);
        }
    }

    private void testRuntimeBadCaseSnapshot() {
        Experience evaluationLoop = experienceRepository.findById("exp-ecom-evaluation-loop").orElse(null);
        String badCasesContent = evaluationLoop == null ? "" : evaluationLoop.getReferences().stream()
                .filter(reference -> "experiences/bad_cases.yaml".equals(reference.getPath()))
                .findFirst()
                .map(reference -> reference.getContent())
                .orElse("");

        boolean runtimeSnapshotReady = badCasesContent.contains("runtime_bad_case_snapshot:")
                && (badCasesContent.contains("bc_runtime_all_green")
                || badCasesContent.contains("bc_runtime_fast_intent_miss")
                || badCasesContent.contains("bc_runtime_fast_path_execution_gap")
                || badCasesContent.contains("bc_runtime_deep_path_execution_gap")
                || badCasesContent.contains("bc_runtime_deep_path_type_mismatch"));

        if (runtimeSnapshotReady) {
            log.info("ExperienceTestValidator#testRuntimeBadCaseSnapshot - reason=✅ runtime bad case snapshot generated");
        }
        else {
            log.warn("ExperienceTestValidator#testRuntimeBadCaseSnapshot - reason=❌ runtime bad case snapshot missing");
        }
    }

    private void testWarehouseBootstrap() {
        Integer coreMetricRows = warehouseQueryService.countRows("ads_daily_core_metrics");
        Integer regionRows = warehouseQueryService.countRows("ads_region_daily");
        Integer categoryRows = warehouseQueryService.countRows("ads_category_daily");
        List<Map<String, Object>> gmvRows = warehouseQueryService.getDailyCoreMetrics(java.time.LocalDate.of(2026, 5, 17));

        log.info("ExperienceTestValidator#testWarehouseBootstrap - reason=warehouse stats coreMetricRows={}, regionRows={}, categoryRows={}",
                coreMetricRows, regionRows, categoryRows);

        if (coreMetricRows != null && coreMetricRows >= 2
                && regionRows != null && regionRows >= 4
                && categoryRows != null && categoryRows >= 6
                && !gmvRows.isEmpty()) {
            log.info("ExperienceTestValidator#testWarehouseBootstrap - reason=✅ demo warehouse ready for GMV/区域/品类 query chain");
        }
        else {
            log.warn("ExperienceTestValidator#testWarehouseBootstrap - reason=❌ demo warehouse incomplete for ecommerce query chain");
        }
    }

    private void testFastIntentSelection() {
        ExperienceQuery query = new ExperienceQuery(ExperienceType.REACT);
        query.setRetrievalMode(ExperienceQuery.RetrievalMode.FULL_SCAN);
        query.setLimit(20);
        List<Experience> reactExperiences = experienceProvider.query(query, new ExperienceQueryContext());

        validateFastIntentHit("昨天 GMV 多少？", "exp-ecom-fast-gmv-yesterday", reactExperiences);
        validateFastIntentHit("华东和华南哪个区域表现更差？", "exp-ecom-fast-region-compare", reactExperiences);
        validateFastIntentHit("品类排行怎么看？", "exp-ecom-fast-category-rank", reactExperiences);
        validateFastIntentHit("昨天 DAU 和活跃买家多少？", "exp-ecom-fast-user-overview", reactExperiences);
        validateFastIntentHit("哪个渠道转化更高？", "exp-ecom-fast-channel-conversion", reactExperiences);
        validateFastIntentHit("华东 GMV 为什么跌了？", "exp-ecom-fast-root-cause-east-drop", reactExperiences);
    }

    private void testRootCauseReactPlan() {
        Experience experience = experienceRepository.findById("exp-ecom-fast-root-cause-east-drop").orElse(null);
        if (experience == null || experience.getArtifact() == null || experience.getArtifact().getReact() == null
                || experience.getArtifact().getReact().getPlan() == null) {
            log.warn("ExperienceTestValidator#testRootCauseReactPlan - reason=❌ root cause react plan missing");
            return;
        }

        List<com.alibaba.assistant.agent.extension.experience.model.ExperienceArtifact.ToolCallSpec> toolCalls =
                experience.getArtifact().getReact().getPlan().getToolCalls();
        List<String> toolChain = toolCalls.stream()
                .map(com.alibaba.assistant.agent.extension.experience.model.ExperienceArtifact.ToolCallSpec::getToolName)
                .toList();
        List<String> expectedToolChain = List.of(
                "RegionPerformanceQueryTool",
                "RegionPerformanceQueryTool",
                "OrderQueryTool",
                "OrderQueryTool",
                "UserMetricTool",
                "UserMetricTool",
                "CategoryRankTool",
                "CategoryRankTool",
                "FunnelAnalysisTool",
                "FunnelAnalysisTool",
                "RefundAnalysisTool"
        );

        if (expectedToolChain.equals(toolChain)) {
            log.info("ExperienceTestValidator#testRootCauseReactPlan - reason=✅ root cause plan locked toolChain={}", toolChain);
        }
        else {
            log.warn("ExperienceTestValidator#testRootCauseReactPlan - reason=❌ root cause plan drift expected={}, actual={}",
                    expectedToolChain, toolChain);
        }
    }

    private void validateFastIntentHit(String question, String expectedExperienceId, List<Experience> reactExperiences) {
        java.util.Optional<Experience> hit = fastIntentService.selectBestMatch(
                reactExperiences,
                new FastIntentContext(question, List.of(), Map.of(), null, null)
        );

        if (hit.isPresent() && expectedExperienceId.equals(hit.get().getId())) {
            log.info("ExperienceTestValidator#validateFastIntentHit - reason=✅ fast-intent matched question={}, expId={}",
                    question, expectedExperienceId);
        }
        else {
            log.warn("ExperienceTestValidator#validateFastIntentHit - reason=❌ fast-intent mismatch question={}, expectedExpId={}, actualExpId={}",
                    question,
                    expectedExperienceId,
                    hit.map(Experience::getId).orElse("NONE"));
        }
    }

    private int countOccurrences(String text, String token) {
        if (text == null || text.isBlank() || token == null || token.isBlank()) {
            return 0;
        }
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }
}
