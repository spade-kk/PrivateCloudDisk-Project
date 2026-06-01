package org.project.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Slf4j
public class TimerAspect {
    /**
     * 计算业务方法耗时
     * @param pjp
     * @return
     * @throws Throwable
     */
    @Around("execution(* org.project.service.impl.*.*(..))")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        //开始时间
        long start = System.currentTimeMillis();
        //调用目标方法,比如login方法,getByUid方法
        Object result = pjp.proceed();
        //结束时间
        long end = System.currentTimeMillis();
        log.info("[业务类 " + pjp.getTarget().getClass().getName() + ": " + pjp.getSignature().getName() + "方法] 耗时:"+(end-start));
        return result;
    }
}
