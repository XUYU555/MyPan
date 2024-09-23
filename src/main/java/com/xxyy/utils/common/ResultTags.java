package com.xxyy.utils.common;

/**
 * @author xy
 * @date 2023-09-13 12:36
 */
public enum ResultTags  implements ResultTag{
    SUCCEEDED("success" ,200, "请求成功"),

    FAILED("fail", 500, "请求失败"),
    ;

    private final String status;
    private final Integer code;
    private final String info;

    ResultTags(String status, Integer code, String info) {
        this.status = status;
        this.code = code;
        this.info = info;
    }

    @Override
    public Integer getCode() {
        return code;
    }

    @Override
    public String getInfo() {
        return info;
    }

    @Override
    public String getStatus() {
        return status;
    }
}
