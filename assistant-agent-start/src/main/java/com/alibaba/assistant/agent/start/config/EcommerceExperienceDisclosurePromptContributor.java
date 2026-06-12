package com.alibaba.assistant.agent.start.config;

import com.alibaba.assistant.agent.common.constant.CodeactStateKeys;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.DirectExperienceGrounding;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.ExperienceCandidateCard;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePayloads.GroupedExperienceCandidates;
import com.alibaba.assistant.agent.extension.experience.disclosure.ExperienceDisclosurePromptContributor;
import com.alibaba.assistant.agent.prompt.PromptContribution;
import com.alibaba.assistant.agent.prompt.PromptContributorContext;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class EcommerceExperienceDisclosurePromptContributor extends ExperienceDisclosurePromptContributor {

    private static final String BUSINESS_CAPABILITY_EXPERIENCE_ID = "exp-ecom-business-capability-map";
    private final VerifiedCaseCatalog verifiedCaseCatalog;

    public EcommerceExperienceDisclosurePromptContributor(VerifiedCaseCatalog verifiedCaseCatalog) {
        super(18);
        this.verifiedCaseCatalog = verifiedCaseCatalog;
    }

    @Override
    public PromptContribution contribute(PromptContributorContext context) {
        GroupedExperienceCandidates candidates = context.getAttribute(
                CodeactStateKeys.EXPERIENCE_PREFETCHED_CANDIDATES, GroupedExperienceCandidates.class).orElse(null);
        List<DirectExperienceGrounding> directGroundings = context
                .getAttribute(CodeactStateKeys.EXPERIENCE_DIRECT_GROUNDINGS, List.class)
                .map(this::normalizeDirectGroundings)
                .orElse(List.of());
        if ((candidates == null || !hasAnyCandidates(candidates)) && directGroundings.isEmpty()) {
            return PromptContribution.empty();
        }

        String query = context.getAttribute(CodeactStateKeys.EXPERIENCE_PREFETCH_QUERY, String.class).orElse("");
        boolean productPositioningQuery = isProductPositioningQuery(query);
        boolean verifiedCaseMetadataQuery = isVerifiedCaseMetadataQuery(query);

        GroupedExperienceCandidates prioritizedCandidates = productPositioningQuery
                ? prioritizeBusinessCapabilityCandidate(candidates)
                : candidates;
        List<DirectExperienceGrounding> prioritizedGroundings = productPositioningQuery
                ? prioritizeBusinessCapabilityGrounding(directGroundings)
                : directGroundings;

        String prompt = buildPrompt(prioritizedCandidates, prioritizedGroundings);
        if (productPositioningQuery) {
            prompt = "<business_positioning_priority>\n"
                    + "当前问题更偏产品定位、业务价值或竞品差异说明。优先使用 `exp-ecom-business-capability-map` 解释："
                    + "这个 Agent 解决哪类组织问题、为什么比普通问数或 BI demo 更进一步、以及它与阿里/京东/拼多多现状的对应关系。\n"
                    + "</business_positioning_priority>\n\n"
                    + prompt;
        }
        if (productPositioningQuery || verifiedCaseMetadataQuery) {
            String verifiedCaseBrief = verifiedCaseCatalog.renderPromptBrief(query, 3);
            if (!verifiedCaseBrief.isBlank()) {
                prompt = verifiedCaseBrief + prompt;
            }
        }

        return PromptContribution.builder()
                .append(new UserMessage(prompt))
                .build();
    }

    private boolean isProductPositioningQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        return normalized.contains("产品定位")
                || normalized.contains("业务价值")
                || normalized.contains("竞品")
                || normalized.contains("差异")
                || normalized.contains("阿里")
                || normalized.contains("京东")
                || normalized.contains("拼多多")
                || normalized.contains("这个agent")
                || normalized.contains("这个项目")
                || normalized.contains("解决什么问题")
                || normalized.contains("为什么值得做");
    }

    private boolean isVerifiedCaseMetadataQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        return normalized.contains("角色")
                || normalized.contains("framework")
                || normalized.contains("capability")
                || normalized.contains("能力")
                || normalized.contains("fastintent")
                || normalized.contains("experience")
                || normalized.contains("tool")
                || normalized.contains("对标")
                || normalized.contains("阿里")
                || normalized.contains("京东")
                || normalized.contains("拼多多");
    }

    private GroupedExperienceCandidates prioritizeBusinessCapabilityCandidate(GroupedExperienceCandidates candidates) {
        if (candidates == null) {
            return null;
        }
        GroupedExperienceCandidates copy = new GroupedExperienceCandidates();
        copy.setCommonCandidates(moveCandidateFirst(candidates.getCommonCandidates(), BUSINESS_CAPABILITY_EXPERIENCE_ID));
        copy.setReactCandidates(candidates.getReactCandidates());
        copy.setToolCandidates(candidates.getToolCandidates());
        return copy;
    }

    private List<DirectExperienceGrounding> prioritizeBusinessCapabilityGrounding(List<DirectExperienceGrounding> groundings) {
        if (groundings == null || groundings.isEmpty()) {
            return groundings;
        }
        List<DirectExperienceGrounding> reordered = new ArrayList<>(groundings.size());
        DirectExperienceGrounding target = null;
        for (DirectExperienceGrounding grounding : groundings) {
            if (grounding != null && BUSINESS_CAPABILITY_EXPERIENCE_ID.equals(grounding.getId())) {
                target = grounding;
            }
            else if (grounding != null) {
                reordered.add(grounding);
            }
        }
        if (target != null) {
            reordered.add(0, target);
            return reordered;
        }
        return groundings;
    }

    private List<ExperienceCandidateCard> moveCandidateFirst(List<ExperienceCandidateCard> cards, String targetId) {
        if (cards == null || cards.isEmpty()) {
            return cards;
        }
        List<ExperienceCandidateCard> reordered = new ArrayList<>(cards.size());
        ExperienceCandidateCard target = null;
        for (ExperienceCandidateCard card : cards) {
            if (card != null && targetId.equals(card.getId())) {
                target = card;
            }
            else if (card != null) {
                reordered.add(card);
            }
        }
        if (target != null) {
            reordered.add(0, target);
            return reordered;
        }
        return cards;
    }

    private boolean hasAnyCandidates(GroupedExperienceCandidates candidates) {
        return candidates != null
                && (!(candidates.getCommonCandidates() == null || candidates.getCommonCandidates().isEmpty())
                || !(candidates.getReactCandidates() == null || candidates.getReactCandidates().isEmpty())
                || !(candidates.getToolCandidates() == null || candidates.getToolCandidates().isEmpty()));
    }

    @SuppressWarnings("unchecked")
    private List<DirectExperienceGrounding> normalizeDirectGroundings(List<?> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        ArrayList<DirectExperienceGrounding> normalized = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof DirectExperienceGrounding grounding) {
                normalized.add(grounding);
            }
        }
        return normalized;
    }
}
