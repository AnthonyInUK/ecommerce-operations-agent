package com.alibaba.assistant.agent.start.observability;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 横切日志埋点标记。打在 Spring Bean 的 public 方法上，由 {@link LogAroundAspect}
 * 统一拦截：记录方法耗时、成功/异常，把"观测逻辑"从业务代码里解耦出去。
 *
 * <p>注意：Spring AOP 基于代理，只对外部调用（经过代理）的 public 方法生效，
 * 类内部 this.xxx() 的自调用不会被拦截，因此请标注真正的对外入口方法。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogAround {

    /**
     * 可选的埋点名称；为空时使用"目标类名#方法名"。
     */
    String value() default "";
}
