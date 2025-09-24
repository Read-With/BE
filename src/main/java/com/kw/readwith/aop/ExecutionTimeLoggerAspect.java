package com.kw.readwith.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Aspect
@Component
public class ExecutionTimeLoggerAspect {

    private static final Logger log = LoggerFactory.getLogger(ExecutionTimeLoggerAspect.class);

    // 시간을 측정할 대상을 지정: service 패키지 내의 모든 public 메서드
    @Pointcut("execution(* com.kw.readwith.service..*.*(..))")
    private void allServiceMethods() {}

    // Pointcut으로 지정된 메서드의 실행 전후에 개입
    @Around("allServiceMethods()")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        Object result = joinPoint.proceed();

        stopWatch.stop();

        String details = "";
        if (result instanceof Integer && (Integer) result > 0) {
            details = String.format(" - 처리된 항목: %d개", result);
        }

        log.info("[ExecutionTime] {}.{} - 총 소요 시간: {} ms{}",
                joinPoint.getTarget().getClass().getSimpleName(), // 클래스 이름
                joinPoint.getSignature().getName(),               // 메서드 이름
                stopWatch.getTotalTimeMillis(),                   // 소요 시간
                details);                                         // 처리된 항목
        return result;
    }
}