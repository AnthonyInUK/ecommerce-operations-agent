package com.alibaba.assistant.agent.start.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * API Key 鉴权过滤器：对 {@code /api/ecommerce/**} 校验 {@code X-API-Key} 请求头。
 *
 * <p>未配置 key（{@link ApiKeySecurityProperties#isEnforced()} 为 false）时直接放行，
 * 保持本地/演示零门槛；配置后，缺失或非法 key 一律返回 401 JSON。
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthFilter.class);
    private static final String PROTECTED_PREFIX = "/api/ecommerce/";

    private final ApiKeySecurityProperties properties;

    public ApiKeyAuthFilter(ApiKeySecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (!properties.isEnforced() || !isProtected(request)) {
            chain.doFilter(request, response);
            return;
        }

        String presented = request.getHeader(properties.getHeader());
        List<String> allowed = properties.getApiKeys();
        if (presented != null && allowed.contains(presented)) {
            // 校验通过：放一个已认证的 Authentication 进上下文，供后续鉴权/审计使用。
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    "api-key-client", null, AuthorityUtils.createAuthorityList("ROLE_API"));
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
            return;
        }

        log.warn("ApiKeyAuthFilter - reason=rejected, path={}, hasKey={}", request.getRequestURI(), presented != null);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"缺少或非法的 API Key（请求头 "
                + properties.getHeader() + "）\"}");
    }

    private boolean isProtected(HttpServletRequest request) {
        return request.getRequestURI().startsWith(PROTECTED_PREFIX);
    }
}
