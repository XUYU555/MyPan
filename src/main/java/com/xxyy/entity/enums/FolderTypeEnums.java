package com.xxyy.entity.enums;

import lombok.Getter;

/**
 * @author xy
 * @date 2024-09-24 16:41
 */

@Getter
public enum FolderTypeEnums {
    FOLDER(1, "目录"),
    DOCUMENT(0, "文件")
    ;

    private final Integer type;

    private final String msg;

    FolderTypeEnums(Integer type, String msg) {
        this.type = type;
        this.msg = msg;
    }
}
