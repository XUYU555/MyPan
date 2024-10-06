package com.xxyy.entity.enums;

/**
 * @author xy
 * @date 2024-10-05 22:59
 */

public enum ShareValidTypeEnums {

    ONE_DAY(0, 1, "1天"),
    WEEK(1, 7, "7天"),
    MONTH(2, 30,"30天"),
    PERMANENT(3, 999,"永久")
    ;

    private final Integer type;

    private final Integer expire;

    private final String message;

    ShareValidTypeEnums(Integer type, Integer expire, String message) {
        this.type = type;
        this.expire = expire;
        this.message = message;
    }

    public Integer getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public Integer getExpire() {
        return expire;
    }

    public static ShareValidTypeEnums getShareValidType(Integer valid) {
        for (ShareValidTypeEnums typeEnums : ShareValidTypeEnums.values()) {
            if (typeEnums.type.intValue() == valid) {
                return typeEnums;
            }
        }
        return null;
    }
}
