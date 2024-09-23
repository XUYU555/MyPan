package com.xxyy.annotation;

import org.springframework.web.bind.annotation.Mapping;

import java.lang.annotation.*;

/**
 * @author xy
 * @date 2024-09-18 16:08
 */


@Documented
@Mapping
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface GlobalInterceptor {

    /**
     * 校验参数
     * @return
     */
    boolean checkParams() default false;

    /**
     * 校验登陆
     * @return
     */
    boolean checkLoing() default true;

    /**
     * 校验管理员
     * @return
     */
    boolean checkAdmin() default false;
}
