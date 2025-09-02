package com.ywluv.easy_http_log_aspect.utils;

import com.ywluv.easy_http_log_aspect.dto.HttpRequestLog;
import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;

/**
 * packageName    : com.ywluv.easy_http_log_aspect.utils
 * fileName       : HttpLoggingUtils
 * author         : MYH
 * date           : 2025-09-02
 * description    :
 * ===========================================================
 * DATE              AUTHOR             NOTE
 * -----------------------------------------------------------
 * 2025-09-02        MYH       최초 생성
 */
public class HttpLoggingUtils {

    public static String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    public static HttpRequestLog buildHttpRequestLog(HttpServletRequest request, String requestBodyString, String responseBodyString) {
        String query = request.getQueryString() != null ? "?" + request.getQueryString() : "";
        String requestString = String.format("%s %s%s, Params: %s",
                request.getMethod(),
                request.getRequestURI(),
                query,
                requestBodyString);
        return HttpRequestLog.builder()
                .createdDateTime(LocalDateTime.now())
                .requesterIp(request != null ? getClientIp(request) : "N/A")
                .request(requestString)
                .response(responseBodyString)
                .build();
    }
}
