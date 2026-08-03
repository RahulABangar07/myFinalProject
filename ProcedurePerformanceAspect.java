package com.example.aspect;

import com.example.annotation.LogStoredProcedure;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Aspect
@Component
public class ProcedurePerformanceAspect {

    // Route performance items specifically to the dedicated Logback logger configuration context
    private static final Logger perfLogger = LoggerFactory.getLogger("db.performance.logger");
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    @Around("@annotation(logStoredProcedure)")
    public Object profileProcedureExecution(ProceedingJoinPoint joinPoint, LogStoredProcedure logStoredProcedure) throws Throwable {
        Instant startTime = Instant.now();
        String status = "SUCCESS";
        String exceptionMsg = null;
        Object result = null;

        // 1. Resolve Stored Procedure Identifier Name
        String procedureName = logStoredProcedure.value();
        if (procedureName.isEmpty()) {
            procedureName = joinPoint.getSignature().getName(); // Default fallback to method name
        }

        try {
            // Execute the actual database method call
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            status = "ERROR";
            exceptionMsg = t.getMessage();
            throw t; // Ensure transactional rollbacks remain unaffected
        } finally {
            Instant endTime = Instant.now();
            long durationMs = java.time.Duration.between(startTime, endTime).toMillis();

            // 2. Dynamically Capture Input Parameter Names and Arguments Mapping
            Map<String, Object> paramMap = new LinkedHashMap<>();
            try {
                MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                String[] parameterNames = signature.getParameterNames();
                Object[] args = joinPoint.getArgs();
                
                if (parameterNames != null) {
                    for (int i = 0; i < parameterNames.length; i++) {
                        paramMap.put(parameterNames[i], args[i]);
                    }
                }
            } catch (Exception ex) {
                paramMap.put("errorExtractingParams", ex.getMessage());
            }

            // 3. Assemble Structured JSON Schema Payload
            Map<String, Object> logPayload = new LinkedHashMap<>();
            logPayload.put("procedure", procedureName);
            logPayload.put("parameters", paramMap);
            logPayload.put("startTime", startTime.toString());
            logPayload.put("endTime", endTime.toString());
            logPayload.put("durationMs", durationMs);
            logPayload.put("status", status);

            if (exceptionMsg != null) {
                logPayload.put("error", exceptionMsg);
            }

            // 4. Output cleanly directly to Logback Appender System
            try {
                String jsonLogLine = jsonMapper.writeValueAsString(logPayload);
                if ("ERROR".equals(status)) {
                    perfLogger.error(jsonLogLine);
                } else {
                    perfLogger.info(jsonLogLine);
                }
            } catch (Exception jsonEx) {
                perfLogger.error("FallBack: Procedure {} finished in {}ms with status {}", procedureName, durationMs, status);
            }
        }
    }
}
