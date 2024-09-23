package com.xxyy.entity.enums;

import lombok.Getter;

/**
 * @author xy
 * @date 2024-09-23 20:26
 */

@Getter
public enum FileStatusEnums {
    TRANSFER(0, "转码中"),
    TRANSFER_FAIL(1, "转码失败"),
    USING(2, "使用中")
    ;

    private final Integer code;

    private final String message;

    FileStatusEnums(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
