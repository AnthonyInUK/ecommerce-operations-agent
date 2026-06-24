package com.alibaba.assistant.agent.start.security;

import java.util.List;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ApiKeyAuthFilter 单测：用 Mock 请求/响应验证放行与拦截，不启动 Spring 容器。
 */
class ApiKeyAuthFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private ApiKeySecurityProperties props(boolean enabled, List<String> keys) {
        ApiKeySecurityProperties p = new ApiKeySecurityProperties();
        p.setEnabled(enabled);
        p.setApiKeys(keys);
        return p;
    }

    @Test
    void protectedPath_noKey_returns401AndStopsChain() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(props(true, List.of("k1")));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/ecommerce/answer");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        assertEquals(401, resp.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void protectedPath_validKey_proceedsAndAuthenticates() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(props(true, List.of("k1")));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/ecommerce/answer");
        req.addHeader("X-API-Key", "k1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        assertEquals(200, resp.getStatus());
        verify(chain).doFilter(req, resp);
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void notEnforced_whenNoKeysConfigured_proceeds() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(props(true, List.of()));
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/ecommerce/answer");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        assertEquals(200, resp.getStatus());
    }

    @Test
    void unprotectedPath_skipsCheck() throws Exception {
        ApiKeyAuthFilter filter = new ApiKeyAuthFilter(props(true, List.of("k1")));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }
}
