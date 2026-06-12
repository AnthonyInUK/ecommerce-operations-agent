package com.alibaba.assistant.agent.start.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.operations")
public class AppOperationsProperties {

    private final DailyReport dailyReport = new DailyReport();
    private final GmvDropWatch gmvDropWatch = new GmvDropWatch();

    public DailyReport getDailyReport() {
        return dailyReport;
    }

    public GmvDropWatch getGmvDropWatch() {
        return gmvDropWatch;
    }

    public static class DailyReport {

        private boolean enabled;

        private String cron = "0 0 8 * * *";

        private String sourceId = "ecommerce-ops";

        private String createdBy = "system";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public String getSourceId() {
            return sourceId;
        }

        public void setSourceId(String sourceId) {
            this.sourceId = sourceId;
        }

        public String getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(String createdBy) {
            this.createdBy = createdBy;
        }
    }

    public static class GmvDropWatch {

        private boolean enabled;

        private String cron = "0 15 8 * * *";

        private String sourceId = "ecommerce-ops";

        private String createdBy = "system";

        private double relativeDropThreshold = 0.15d;

        private double weekOverWeekDropThreshold = 0.15d;

        private int rollingAverageWindowDays = 7;

        private double rollingAverageDropThreshold = 0.12d;

        private double yearOverYearDropThreshold = 0.15d;

        private double specialPeriodThresholdMultiplier = 1.5d;

        private double minNotifyAbsoluteGmvDrop = 500d;

        private double minNotifyDropRate = 0.02d;

        private String demoReportDate;

        private List<String> holidayDates = new ArrayList<>();

        private List<String> activityWindows = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public String getSourceId() {
            return sourceId;
        }

        public void setSourceId(String sourceId) {
            this.sourceId = sourceId;
        }

        public String getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(String createdBy) {
            this.createdBy = createdBy;
        }

        public double getRelativeDropThreshold() {
            return relativeDropThreshold;
        }

        public void setRelativeDropThreshold(double relativeDropThreshold) {
            this.relativeDropThreshold = relativeDropThreshold;
        }

        public double getWeekOverWeekDropThreshold() {
            return weekOverWeekDropThreshold;
        }

        public void setWeekOverWeekDropThreshold(double weekOverWeekDropThreshold) {
            this.weekOverWeekDropThreshold = weekOverWeekDropThreshold;
        }

        public int getRollingAverageWindowDays() {
            return rollingAverageWindowDays;
        }

        public void setRollingAverageWindowDays(int rollingAverageWindowDays) {
            this.rollingAverageWindowDays = rollingAverageWindowDays;
        }

        public double getRollingAverageDropThreshold() {
            return rollingAverageDropThreshold;
        }

        public void setRollingAverageDropThreshold(double rollingAverageDropThreshold) {
            this.rollingAverageDropThreshold = rollingAverageDropThreshold;
        }

        public double getYearOverYearDropThreshold() {
            return yearOverYearDropThreshold;
        }

        public void setYearOverYearDropThreshold(double yearOverYearDropThreshold) {
            this.yearOverYearDropThreshold = yearOverYearDropThreshold;
        }

        public double getSpecialPeriodThresholdMultiplier() {
            return specialPeriodThresholdMultiplier;
        }

        public void setSpecialPeriodThresholdMultiplier(double specialPeriodThresholdMultiplier) {
            this.specialPeriodThresholdMultiplier = specialPeriodThresholdMultiplier;
        }

        public String getDemoReportDate() {
            return demoReportDate;
        }

        public void setDemoReportDate(String demoReportDate) {
            this.demoReportDate = demoReportDate;
        }

        public List<String> getHolidayDates() {
            return holidayDates;
        }

        public void setHolidayDates(List<String> holidayDates) {
            this.holidayDates = holidayDates;
        }

        public List<String> getActivityWindows() {
            return activityWindows;
        }

        public void setActivityWindows(List<String> activityWindows) {
            this.activityWindows = activityWindows;
        }

        public double getMinNotifyAbsoluteGmvDrop() {
            return minNotifyAbsoluteGmvDrop;
        }

        public void setMinNotifyAbsoluteGmvDrop(double minNotifyAbsoluteGmvDrop) {
            this.minNotifyAbsoluteGmvDrop = minNotifyAbsoluteGmvDrop;
        }

        public double getMinNotifyDropRate() {
            return minNotifyDropRate;
        }

        public void setMinNotifyDropRate(double minNotifyDropRate) {
            this.minNotifyDropRate = minNotifyDropRate;
        }
    }
}
