package com.ywluv.easy_http_log_aspect.handler;

import com.ywluv.easy_http_log_aspect.dto.HttpRequestLog;

public interface HttpRequestLogHandler {
    void handle(HttpRequestLog httpRequestLog);
}
