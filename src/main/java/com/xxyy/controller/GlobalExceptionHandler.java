package com.xxyy.controller;

import com.xxyy.entity.enums.ResponseCodeEnums;
import com.xxyy.utils.common.AppException;
import com.xxyy.utils.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.servlet.http.HttpServletRequest;
import java.net.BindException;

/**
 * @author xy
 * @date 2024-09-21 14:23
 * 全局异常处理类
 */


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = Exception.class)
    public Result<?> handlerAppException(Exception e, HttpServletRequest request) {
        log.error("请求错误，请求地址{}，错误信息: ", request.getRequestURI(), e);
        Result<Object> resultEx = new Result<>();
        resultEx.setStatus("fail");
        // 404
        if (e instanceof NoHandlerFoundException) {
            resultEx.setCode(ResponseCodeEnums.CODE_404.getCode());
            resultEx.setInfo(ResponseCodeEnums.CODE_404.getMsg());
        } else if(e instanceof AppException) {
            // 业务错误
            AppException ex = (AppException) e;
            resultEx.setCode(ex.getCode() == null? ResponseCodeEnums.CODE_500.getCode(): ex.getCode());
            resultEx.setInfo(ex.getMessage() == null? ResponseCodeEnums.CODE_500.getMsg(): ex.getMessage());
        } else if (e instanceof BindException || e instanceof MethodArgumentTypeMismatchException) {
            // 参数错误
            resultEx.setCode(ResponseCodeEnums.CODE_600.getCode());
            resultEx.setInfo(ResponseCodeEnums.CODE_600.getMsg());
        } else if (e instanceof DuplicateKeyException) {
            // 主键冲突
            resultEx.setCode(ResponseCodeEnums.CODE_601.getCode());
            resultEx.setInfo(ResponseCodeEnums.CODE_601.getMsg());
        } else {
            resultEx.setCode(ResponseCodeEnums.CODE_404.getCode());
            resultEx.setInfo(ResponseCodeEnums.CODE_404.getMsg());
        }
        return resultEx;
    }
}
