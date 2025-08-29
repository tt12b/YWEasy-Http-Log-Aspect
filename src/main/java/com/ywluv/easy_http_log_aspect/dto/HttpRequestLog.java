package com.ywluv.easy_http_log_aspect.dto;

import lombok.Builder;

import java.time.LocalDateTime;


@Builder
public record HttpRequestLog(
        LocalDateTime createdDateTime,
        String requesterIp,
        String request,
        String response
) {}
