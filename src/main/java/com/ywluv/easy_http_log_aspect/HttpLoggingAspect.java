package com.ywluv.easy_http_log_aspect;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.*;

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
        // --- 로그 가능 여부 체크 (메서드 우선) ---
        boolean loggable = (methodAnno == null || methodAnno.value()) && (classAnno == null || classAnno.value());
        if (!loggable) {
            return joinPoint.proceed(); // false면 원래 로직 실행
        }



        Set<String> excludeColumns = new HashSet<>();
        if (methodAnno != null) {
            // 메서드 어노테이션이 있으면 클래스 컬럼 무시
            excludeColumns.addAll(Arrays.asList(methodAnno.excludeColumns()));
        } else if (classAnno != null) {
            excludeColumns.addAll(Arrays.asList(classAnno.excludeColumns()));
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

                //민감 정보가 담긴 쿼리스트링도 삭제
                String originalQuery = request.getQueryString();
                String maskedQueryString = "";
                if (originalQuery != null && !originalQuery.isEmpty()) {
                    // 쿼리스트링 분리
                    List<String> parts = Arrays.asList(originalQuery.split("&"));

                    // 민감 컬럼 마스킹
                    List<String> maskedParts = parts.stream()
                            .map(p -> {
                                String[] kv = p.split("=", 2);
                                String key = kv[0];
                                String value = kv.length > 1 ? kv[1] : "";
                                if (excludeColumns.contains(key)) {
                                    value = "***"; // 마스킹 처리
                                }
                                return key + "=" + value;
                            })
                            .toList();

                    maskedQueryString = String.join("&", maskedParts);
                }

                //body request
                String bodyString = maskAndSerializeParams(params(joinPoint), excludeColumns);

                requestString = String.format(
                        "%s %s?%s, Params: %s",
                        request.getMethod(),
                        request.getRequestURI(),
                        maskedQueryString,
                        bodyString
                );
            } else {
                requestString = "N/A";
            }

            String responseString;
            if (exception != null) {
                log.error("Exception during API execution: {}", exception.getMessage(), exception);
                responseString = "Exception: " + exception.getMessage();
            } else {
                Object loggableReturn = returnObj;
                // 민감 컬럼 마스킹
                if (returnObj != null && !excludeColumns.isEmpty()) {
                    if (returnObj instanceof Map<?, ?> map) {
                        Map<String, Object> maskedMap = new HashMap<>((Map<String, Object>) map);
                        excludeColumns.forEach(key -> {
                            if (maskedMap.containsKey(key)) {
                                maskedMap.put(key, "***"); // 제거 대신 마스킹
                            }
                        });
                        loggableReturn = maskedMap;
                    } else {
                        // DTO일 경우 ObjectNode로 변환 후 필드 마스킹
                        ObjectNode node = objectMapper.valueToTree(returnObj);
                        excludeColumns.forEach(key -> {
                            if (node.has(key)) {
                                node.put(key, "***"); // 제거 대신 마스킹
                            }
                        });
                        loggableReturn = node;
                    }
                }

                // JSON 직렬화만 try/catch
                try {
                    responseString = loggableReturn != null ? objectMapper.writeValueAsString(loggableReturn) : null;
                } catch (JsonProcessingException e) {
                    log.error("Failed to convert response to JSON", e);
                    responseString = loggableReturn != null ? loggableReturn.toString() : null;
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


    private String maskAndSerializeParams(Map<String, Object> paramMap, Set<String> excludeColumns) {
        if (paramMap == null || paramMap.isEmpty()) {
            return "{}";
        }

        // DTO 내부 필드 마스킹
        for (Map.Entry<String, Object> entry : paramMap.entrySet()) {
            Object value = entry.getValue();
            if (value == null) continue;

            if (value instanceof Map<?, ?>) {
                // Map이면 key 기준 마스킹
                Map<String, Object> map = (Map<String, Object>) value; // 캐스팅
                excludeColumns.forEach(k -> {
                    if (map.containsKey(k)) map.put(k, "***");
                });
            } else {
                // DTO일 경우 reflection으로 필드 마스킹
                excludeColumns.forEach(fieldName -> {
                    try {
                        Field field = value.getClass().getDeclaredField(fieldName);
                        field.setAccessible(true);
                        field.set(value, "***");
                    } catch (NoSuchFieldException | IllegalAccessException ignored) {
                        // 필드가 없으면 무시
                    }
                });
            }
        }

        // JSON 문자열로 직렬화
        try {
            return new ObjectMapper().writeValueAsString(paramMap);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return paramMap.toString(); // 직렬화 실패 시 fallback
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
