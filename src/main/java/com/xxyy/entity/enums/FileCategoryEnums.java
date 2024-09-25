package com.xxyy.entity.enums;

import lombok.Getter;

/**
 * @author xy
 * @date 2024-09-22 12:27
 * 文件类别
 */

@Getter
public enum FileCategoryEnums {

    ALL("all", 0L),
    OTHERS("others", 5L),
    MUSIC("music", 2L),
    VIDEO("video", 1L),
    IMAGE("image", 3L),
    DOC("doc", 4L)
            ;

    FileCategoryEnums(String category, Long code) {
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

    private final Long code;

}
