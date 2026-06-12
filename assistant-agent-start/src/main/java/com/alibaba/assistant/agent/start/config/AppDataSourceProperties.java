package com.alibaba.assistant.agent.start.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.datasource")
public class AppDataSourceProperties {

    private boolean enabled = true;

    private String driverClassName = "org.h2.Driver";

    private String url = "jdbc:h2:mem:assistant_agent;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false";

    private String username = "sa";

    private String password = "";

    private boolean bootstrapDemoData = true;

    private boolean bootstrapOlistRawSchema = false;

    private String olistRawImportDir;

    private boolean bootstrapOlistAnalytics = false;

    private boolean preferOlistAnalytics = false;

    private boolean readCacheEnabled = true;

    private long readCacheTtlSeconds = 120;

    private int readCacheMaxEntries = 512;

    private boolean readCacheCacheEmptyResults = false;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isBootstrapDemoData() {
        return bootstrapDemoData;
    }

    public void setBootstrapDemoData(boolean bootstrapDemoData) {
        this.bootstrapDemoData = bootstrapDemoData;
    }

    public boolean isBootstrapOlistRawSchema() {
        return bootstrapOlistRawSchema;
    }

    public void setBootstrapOlistRawSchema(boolean bootstrapOlistRawSchema) {
        this.bootstrapOlistRawSchema = bootstrapOlistRawSchema;
    }

    public String getOlistRawImportDir() {
        return olistRawImportDir;
    }

    public void setOlistRawImportDir(String olistRawImportDir) {
        this.olistRawImportDir = olistRawImportDir;
    }

    public boolean isBootstrapOlistAnalytics() {
        return bootstrapOlistAnalytics;
    }

    public void setBootstrapOlistAnalytics(boolean bootstrapOlistAnalytics) {
        this.bootstrapOlistAnalytics = bootstrapOlistAnalytics;
    }

    public boolean isPreferOlistAnalytics() {
        return preferOlistAnalytics;
    }

    public void setPreferOlistAnalytics(boolean preferOlistAnalytics) {
        this.preferOlistAnalytics = preferOlistAnalytics;
    }

    public boolean isReadCacheEnabled() {
        return readCacheEnabled;
    }

    public void setReadCacheEnabled(boolean readCacheEnabled) {
        this.readCacheEnabled = readCacheEnabled;
    }

    public long getReadCacheTtlSeconds() {
        return readCacheTtlSeconds;
    }

    public void setReadCacheTtlSeconds(long readCacheTtlSeconds) {
        this.readCacheTtlSeconds = readCacheTtlSeconds;
    }

    public int getReadCacheMaxEntries() {
        return readCacheMaxEntries;
    }

    public void setReadCacheMaxEntries(int readCacheMaxEntries) {
        this.readCacheMaxEntries = readCacheMaxEntries;
    }

    public boolean isReadCacheCacheEmptyResults() {
        return readCacheCacheEmptyResults;
    }

    public void setReadCacheCacheEmptyResults(boolean readCacheCacheEmptyResults) {
        this.readCacheCacheEmptyResults = readCacheCacheEmptyResults;
    }
}
