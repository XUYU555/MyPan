package com.xxyy.entity.enums;

import lombok.Getter;

/**
 * @author xy
 * @date 2024-09-22 12:27
 * 文件类别
 */

@Getter
public enum FileCategoryEnums {

    ALL("all", 0),
    OTHERS("others", 5),
    MUSIC("music", 2),
    VIDEO("video", 1),
    IMAGE("image", 3),
    DOC("doc", 4)
            ;

    FileCategoryEnums(String category, Integer code) {
        this.category = category;
        this.code = code;
    }

    public static FileCategoryEnums getCode(String category) {
        for (FileCategoryEnums value : FileCategoryEnums.values()) {
            if (value.getCategory().equals(category)) {
                return value;
            }
        }
        return null;
    }

    private final String category;

    private final Integer code;

}
