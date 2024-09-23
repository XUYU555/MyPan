package com.xxyy.utils.common;

import com.xxyy.entity.enums.ResponseCodeEnums;
import lombok.Getter;

/**
 * @author xy
 * @date 2023-09-12 20:39
 */

@Getter
public class AppException extends RuntimeException{

    private ResponseCodeEnums codeEnum;

    private Integer code;

    private String message;

    public AppException(String message, Throwable e) {
        super(message, e);
        this.message = message;
    }

    public AppException(String message) {
        super(message);
        this.message = message;
    }

    public AppException(ResponseCodeEnums codeEnum) {
        super(codeEnum.getMsg());
        this.codeEnum = codeEnum;
        this.code = codeEnum.getCode();
        this.message = codeEnum.getMsg();
    }

    @Override
    public String getMessage() {
        return super.getMessage();
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}