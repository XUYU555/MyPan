package com.xxyy.entity.enums;

import lombok.Getter;

/**
 * @author xy
 * @date 2024-09-23 16:27
 * 文件删除状态
 */

@Getter
public enum FileDelFlagEnums {
    RECOVERY(1, "进入回收站"),
    NORMAL(2, "正常"),
    DELETE(0, "逻辑删除")
    ;

    private final int code;

    private final String message;

    FileDelFlagEnums(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
