package com.xxyy.entity.enums;

import lombok.Getter;

/**
 * @author xy
 * @date 2024-09-23 16:27
 */

@Getter
public enum FileDelFlagEnums {
    RECOVERY(0, "进入回收站"),
    NORMAL(1, "正常")
    ;

    private final int code;

    private final String message;

    FileDelFlagEnums(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
