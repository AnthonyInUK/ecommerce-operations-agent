package com.alibaba.assistant.agent.start.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 配置：无状态 API + 自定义 API Key 过滤器。
 *
 * <p>引入 spring-security 后默认会用 HTTP Basic 把所有端点保护起来；这里改写过滤链：
 * <ul>
 *   <li>无状态（不建 session）、关闭 CSRF（纯 API，无浏览器表单）；</li>
 *   <li>框架鉴权层全部放行，由 {@link ApiKeyAuthFilter} 作为唯一网关，仅对
 *       {@code /api/ecommerce/**} 且配置了 key 时强制校验；</li>
 *   <li>静态控制台页面与 actuator 健康检查不受影响。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(ApiKeySecurityProperties.class)
public class ApiKeySecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ApiKeySecurityProperties properties)
            throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                // 框架层放行，鉴权交给 ApiKeyAuthFilter（按路径+开关精确控制）。
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new ApiKeyAuthFilter(properties), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
