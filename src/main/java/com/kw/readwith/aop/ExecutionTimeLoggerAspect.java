package com.kw.readwith.aop;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Aspect      // 이 클래스가 Aspect(부가 기능)임을 선언합니다.
@Component   // Spring이 이 클래스를 Bean으로 관리하도록 합니다.
public class ExecutionTimeLoggerAspect {

    private static final Logger log = LoggerFactory.getLogger(ExecutionTimeLoggerAspect.class);

    // Pointcut: 부가 기능을 적용할 대상을 지정합니다.
    // 여기서는 com.kw.readwith.service 패키지 내의 모든 public 메서드를 대상으로 합니다.
    @Pointcut("execution(* com.kw.readwith.service..*.*(..))")
    private void allServiceMethods() {}

    // Around Advice: Pointcut으로 지정된 메서드의 실행 전후에 개입합니다.
    @Around("allServiceMethods()")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        // Spring에서 제공하는 스톱워치. System.currentTimeMillis()보다 사용이 간편합니다.
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        Object result = joinPoint.proceed(); // 원본 메서드(예: uploadRelationships)를 실행합니다.

        stopWatch.stop();

        // 메서드 반환값을 확인하여 로그에 추가 정보 제공
        String details = "";
        if (result instanceof Integer && (Integer) result > 0) {
            details = String.format(" - 처리된 항목: %d개", result);
        }

        log.info("[ExecutionTime] {}.{} - 총 소요 시간: {} ms{}",
                joinPoint.getTarget().getClass().getSimpleName(), // 클래스 이름
                joinPoint.getSignature().getName(),               // 메서드 이름
                stopWatch.getTotalTimeMillis(),                   // 소요 시간
                details);                                         // 추가 정보
        return result;
    }
}