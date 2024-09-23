package com.xxyy.annotation;

import com.xxyy.entity.enums.RegexPattern;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author xy
 * @date 2024-09-18 16:17
 */

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface VerifyParams {

    // 最小值
    int min() default -1;

    // 最大值
    int max() default -1;

    // 必需
    boolean required() default false;

    // 正则
    RegexPattern regex() default RegexPattern.NO;

}
