package com.ywluv.easy_http_log_aspect.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface LoggableApi {
    boolean value() default true;
}
