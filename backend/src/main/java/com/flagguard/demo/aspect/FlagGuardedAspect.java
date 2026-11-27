package com.flagguard.demo.aspect;

import com.flagguard.demo.annotation.FlagGuarded;
import com.flagguard.demo.client.FlagsmithGateway;
import com.flagguard.demo.service.GuardEngineService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class FlagGuardedAspect {
    private final FlagsmithGateway flagsmith;
    private final GuardEngineService guardEngine;

    public FlagGuardedAspect(FlagsmithGateway flagsmith, GuardEngineService guardEngine) {
        this.flagsmith = flagsmith;
        this.guardEngine = guardEngine;
    }

    @Around("@annotation(flagGuarded)")
    public Object monitorFlaggedExecution(ProceedingJoinPoint joinPoint, FlagGuarded flagGuarded) throws Throwable {
        String flag = flagGuarded.flag();
        guardEngine.registerGuard(flag, flagGuarded.maxErrorRate(), flagGuarded.maxLatencyMs());

        if (!flagsmith.isFeatureEnabled(flag)) {
            return joinPoint.proceed();
        }

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            guardEngine.recordOutcome(flag, true, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable ex) {
            guardEngine.recordOutcome(flag, false, System.currentTimeMillis() - start);
            throw ex;
        }
    }
}
