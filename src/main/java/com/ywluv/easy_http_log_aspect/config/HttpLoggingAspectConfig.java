package com.ywluv.easy_http_log_aspect.config;

import com.ywluv.easy_http_log_aspect.HttpLoggingAspect;
import com.ywluv.easy_http_log_aspect.handler.HttpRequestLogHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
@Slf4j
public class HttpLoggingAspectConfig {

    @Bean
    @ConditionalOnMissingBean(HttpRequestLogHandler.class)
    public HttpRequestLogHandler defaultHttpRequestLogHandler() {
        return httpRequestLog -> log.info("DefaultHttpRequestLogHandler: {}", httpRequestLog);
    }

    @Bean
    public HttpLoggingAspect httpLoggingAspect(HttpRequestLogHandler httpRequestLogHandler) {
        return new HttpLoggingAspect(httpRequestLogHandler);
    }

//    @Bean
//    public HttpLoggingAspect httpLoggingAspect(ObjectProvider<HttpRequestLogHandler> provider) {
//        HttpRequestLogHandler handler = provider.getIfAvailable(() ->
//                httpRequestLog -> log.info("DefaultHttpRequestLogHandler: {}", httpRequestLog)
//        );
//        return new HttpLoggingAspect(handler);
//    }
}
