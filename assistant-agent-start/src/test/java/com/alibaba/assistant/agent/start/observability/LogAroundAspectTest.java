package com.alibaba.assistant.agent.start.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LogAroundAspect 单测：用 AspectJProxyFactory 直接织入切面，不启动整个 Spring 上下文，
 * 验证 @LogAround 标注的方法会被环绕拦截——成功时记录耗时，异常时记录并向上抛出。
 */
class LogAroundAspectTest {

    /** 被代理的样例目标：模拟工具的对外入口。 */
    static class SampleService {
        @LogAround
        public String ok(String in) {
            return "echo:" + in;
        }

        @LogAround("custom-label")
        public void boom() {
            throw new IllegalStateException("explode");
        }
    }

    private Logger aspectLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        aspectLogger = (Logger) LoggerFactory.getLogger(LogAroundAspect.class);
        appender = new ListAppender<>();
        appender.start();
        aspectLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        aspectLogger.detachAppender(appender);
    }

    private SampleService proxy() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleService());
        factory.addAspect(new LogAroundAspect());
        return factory.getProxy();
    }

    @Test
    void around_success_logsElapsedAndReturnsResult() {
        String result = proxy().ok("hi");

        assertEquals("echo:hi", result);
        assertTrue(appender.list.stream().anyMatch(e ->
                e.getLevel() == Level.INFO
                        && e.getFormattedMessage().contains("SampleService#ok")
                        && e.getFormattedMessage().contains("耗时")));
    }

    @Test
    void around_exception_logsErrorAndRethrows() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> proxy().boom());

        assertEquals("explode", ex.getMessage());
        // 自定义 value() 应作为埋点名，且记录在 ERROR 级别
        assertTrue(appender.list.stream().anyMatch(e ->
                e.getLevel() == Level.ERROR
                        && e.getFormattedMessage().contains("custom-label")
                        && e.getFormattedMessage().contains("explode")));
    }
}
