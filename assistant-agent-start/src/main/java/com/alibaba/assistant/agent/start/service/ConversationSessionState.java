package com.alibaba.assistant.agent.start.service;

import java.time.LocalDate;

public record ConversationSessionState(String lastIntent,
                                       String lastMetricId,
                                       LocalDate lastDate,
                                       String lastRegionName,
                                       String lastCategoryName,
                                       String lastQuestion,
                                       String pendingClarificationType,
                                       String pendingIntent,
                                       String pendingMetricId,
                                       LocalDate pendingDate,
                                       String pendingRegionName,
                                       String pendingCategoryName) {

    public static ConversationSessionState empty() {
        return new ConversationSessionState(null, null, null, null, null, null, null, null, null, null, null, null);
    }

    public ConversationSessionState with(String intent,
                                         String metricId,
                                         LocalDate statDate,
                                         String regionName,
                                         String categoryName,
                                         String question) {
        return new ConversationSessionState(intent, metricId, statDate, regionName, categoryName, question,
                null, null, null, null, null, null);
    }

    public ConversationSessionState withPendingClarification(String clarificationType,
                                                             String intent,
                                                             String metricId,
                                                             LocalDate statDate,
                                                             String regionName,
                                                             String categoryName,
                                                             String question) {
        return new ConversationSessionState(lastIntent, lastMetricId, lastDate, lastRegionName, lastCategoryName, question,
                clarificationType, intent, metricId, statDate, regionName, categoryName);
    }

    public ConversationSessionState clearPendingClarification() {
        return new ConversationSessionState(lastIntent, lastMetricId, lastDate, lastRegionName, lastCategoryName, lastQuestion,
                null, null, null, null, null, null);
    }
}
