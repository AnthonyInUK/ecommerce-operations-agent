package com.alibaba.assistant.agent.start.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * {@link LogAround} 的环绕切面：统一记录被标注方法的耗时与异常。
 *
 * <p>这是"AOP 拦截 + 现有 OpenTelemetry 上报"的标准组合——业务方法不需要写任何
 * 计时/日志样板，观测逻辑集中在这一处，改一个地方就能调整所有埋点行为。
 *
 * <p>埋点名优先用注解的 value()，否则取"目标类简单名#方法名"，这样即使注解打在
 * 公共基类的入口方法上，日志里也能看出具体是哪个工具子类在执行。
 */
@Aspect
@Component
public class LogAroundAspect {

    private static final Logger log = LoggerFactory.getLogger(LogAroundAspect.class);

    @Around("@annotation(logAround)")
    public Object log(ProceedingJoinPoint pjp, LogAround logAround) throws Throwable {
        String label = resolveLabel(pjp, logAround);
        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            log.info("[LogAround] {} 耗时 {}ms", label, System.currentTimeMillis() - start);
            return result;
        }
        catch (Throwable ex) {
            log.error("[LogAround] {} 异常 耗时 {}ms reason={}",
                    label, System.currentTimeMillis() - start, ex.getMessage());
            throw ex;
        }
    }

    private String resolveLabel(ProceedingJoinPoint pjp, LogAround logAround) {
        if (logAround.value() != null && !logAround.value().isBlank()) {
            return logAround.value();
        }
        // 取运行期真实目标类（子类），而非声明注解的基类，方便区分具体工具。
        String type = pjp.getTarget() != null
                ? pjp.getTarget().getClass().getSimpleName()
                : ((MethodSignature) pjp.getSignature()).getDeclaringType().getSimpleName();
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        return type + "#" + method.getName();
    }
}
