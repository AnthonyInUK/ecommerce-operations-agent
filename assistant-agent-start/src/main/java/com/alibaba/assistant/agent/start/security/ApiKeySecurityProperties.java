package com.alibaba.assistant.agent.start.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API Key 鉴权配置。
 *
 * <p>{@code app.security.api-keys} 为空时<b>不启用</b>鉴权（保持本地/演示零门槛、不影响现有测试）；
 * 配置了一个或多个 key 后，对 {@code /api/ecommerce/**} 强制校验请求头 {@code X-API-Key}。
 */
@ConfigurationProperties(prefix = "app.security")
public class ApiKeySecurityProperties {

    /** 总开关。即使配了 key，置 false 也可临时关闭鉴权。 */
    private boolean enabled = true;

    /** 允许的 API Key 列表。为空表示不鉴权。 */
    private List<String> apiKeys = new ArrayList<>();

    /** 携带 key 的请求头名称。 */
    private String header = "X-API-Key";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(List<String> apiKeys) {
        this.apiKeys = apiKeys;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    /** 是否真正强制鉴权：开关打开且至少配置了一个 key。 */
    public boolean isEnforced() {
        return enabled && apiKeys != null && !apiKeys.isEmpty();
    }
}
