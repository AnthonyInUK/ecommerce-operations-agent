package com.alibaba.assistant.agent.start.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * 开启 Spring Cache 抽象。
 *
 * <p>具体缓存后端由配置切换，业务代码只认 {@code @Cacheable} 注解、与后端解耦：
 * <ul>
 *   <li>默认 {@code spring.cache.type=simple}：进程内 ConcurrentMap，本地/测试零依赖；</li>
 *   <li>docker/prod profile {@code spring.cache.type=redis}：切到 Redis，带 TTL，可跨实例共享。</li>
 * </ul>
 *
 * <p>这取代了早期手写的 ConcurrentMap 读缓存（针对高频的"每日大盘指标"查询），
 * 用标准抽象后换后端不动业务代码。
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
