package com.xxyy.entity.enums;

import lombok.Getter;

/**
 * @author xy
 * @date 2024-09-19 17:05
 */

@Getter
public enum UserStatusEnums {

    EABLE(1, "启用"),

    FORBIDDEN(0, "禁用");

    private final int status;

    private final String msg;

    UserStatusEnums(int status, String msg) {
        this.status = status;
        this.msg = msg;
    }

}
