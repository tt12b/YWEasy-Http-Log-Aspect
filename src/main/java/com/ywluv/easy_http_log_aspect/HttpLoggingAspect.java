package com.ywluv.easy_http_log_aspect;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ywluv.easy_http_log_aspect.annotation.LoggableApi;
import com.ywluv.easy_http_log_aspect.dto.HttpRequestLog;
import com.ywluv.easy_http_log_aspect.handler.HttpRequestLogHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.ywluv.easy_http_log_aspect.utils.HttpLoggingUtils.getClientIp;

@Aspect
@Order(1)
@Slf4j
public class HttpLoggingAspect {

    private final HttpRequestLogHandler httpRequestLogHandler;

    @Autowired
    private ObjectMapper objectMapper;

    public HttpLoggingAspect(HttpRequestLogHandler httpRequestLogHandler) {
        this.httpRequestLogHandler = httpRequestLogHandler;
    }

    @Pointcut("@within(com.ywluv.easy_http_log_aspect.annotation.LoggableApi) || @annotation(com.ywluv.easy_http_log_aspect.annotation.LoggableApi)")
    public void loggableApiMethods() {
    }

    @Around("loggableApiMethods()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Class<?> clazz = joinPoint.getTarget().getClass();

        // 애노테이션 체크
        LoggableApi methodAnno = method.getAnnotation(LoggableApi.class);
        LoggableApi classAnno = clazz.getAnnotation(LoggableApi.class);

        if ((methodAnno != null && !methodAnno.value()) || (classAnno != null && !classAnno.value())) {
            return joinPoint.proceed(); // false면 그냥 원래 로직 실행
        }

        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return joinPoint.proceed(); // 스케줄러, @Async 등이면 그냥 실행


        HttpServletRequest request = attrs.getRequest();
        try {
            request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        } catch (Exception e) {
            log.warn("Request not available: {}", e.getMessage());
        }

        Object returnObj = null;
        Throwable exception = null;

        try {
            // --- @Before 역할 ---
            if (request != null) {
                log.debug("API Request: {} {}?{}", request.getMethod(), request.getRequestURI(), request.getQueryString());
            }
            // 실제 메서드 실행
            returnObj = joinPoint.proceed();
            // --- @AfterReturning 역할 ---
            return returnObj;
        } catch (Throwable ex) {
            exception = ex;
            throw ex; // 예외를 다시 던져서 컨트롤러나 상위 로직이 처리하도록
        } finally {
            // --- After  역할 ---
            String requestString;
            if (request != null) {
                Map<String, Object> paramMap = params(joinPoint);
                String paramsJson;
                try {
                    paramsJson = objectMapper.writeValueAsString(paramMap);
                } catch (Exception e) {
                    paramsJson = paramMap.toString(); // 직렬화 실패 시 fallback
                }

                requestString = String.format(
                        "%s %s?%s, Params: %s",
                        request.getMethod(),
                        request.getRequestURI(),
                        request.getQueryString(),
                        paramsJson
                );
            } else {
                requestString = "N/A";
            }

            String responseString;
            if (exception != null) {
                log.error("Exception during API execution: {}", exception.getMessage(), exception);
                responseString = "Exception: " + exception.getMessage();
            } else {
                try {
                    responseString = returnObj != null ? objectMapper.writeValueAsString(returnObj) : null;
                } catch (JsonProcessingException e) {
                    log.error("Failed to convert response to JSON", e);
                    responseString = returnObj != null ? returnObj.toString() : null;
                }
            }

            httpRequestLogHandler.handle(
                    HttpRequestLog.builder()
                            .createdDateTime(LocalDateTime.now())
                            .requesterIp(request != null ? getClientIp(request) : "N/A")
                            .request(requestString)
                            .response(responseString)
                            .build()
            );
        }
    }




    private Map<String, Object> params(JoinPoint joinPoint) {
        CodeSignature codeSignature = (CodeSignature) joinPoint.getSignature();
        if (codeSignature == null) {
            return Collections.emptyMap();
        }

        String[] parameterNames = codeSignature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        if (parameterNames == null || parameterNames.length == 0) {
            return Collections.emptyMap();
        }

        if (args == null || args.length == 0) {
            return Collections.emptyMap();
        }

        if (parameterNames.length != args.length) {
            return Collections.emptyMap();
        }

        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < parameterNames.length; i++) {
            params.put(parameterNames[i], args[i]);
        }
        return params;
    }


}
