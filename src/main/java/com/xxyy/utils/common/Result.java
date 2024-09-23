package com.xxyy.utils.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

/**
 * @author xy
 * @date 2023-09-12 21:16
 */
@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;


    /**
     * 状态码
     *
     * @mock 200
     */
    private String status;
    /**
     * 响应码
     *
     * @mock 200
     */
    private Integer code;
    /**
     * 响应信息
     *
     * @mock 操作成功
     */
    private String info;
    /**
     * 响应数据
     */
    private T data;
    /**
     * 扩展内容
     */
    @JsonInclude(value = JsonInclude.Include.NON_NULL)
    private Object exts;

    public Result() {
    }

    public static <T> Result<T> of(String status ,Integer code, String msg, T data) {
        Result<T> res = new Result<>();
        res.status = status;
        res.code = code;
        res.info = msg;
        res.data = data;
        return res;
    }

    //region R.data

    public static <T> Result<T> data(T data) {
        return Result.data(ResultTags.SUCCEEDED, data);
    }

    public static <T> Result<T> data(ResultTags tag, T data) {
        return Result.of(tag.getStatus(), tag.getCode(), tag.getInfo(), data);
    }

    //endregion

    //region R.ok

    public static Result<?> ok() {
        return Result.ok(ResultTags.SUCCEEDED);
    }

    public static Result<?> ok(ResultTag tag) {
        return Result.of(tag.getStatus() ,tag.getCode(), tag.getInfo(), null);
    }

    public static Result<?> ok(String msg) {
        return Result.of(ResultTags.SUCCEEDED.getStatus(), ResultTags.SUCCEEDED.getCode(), msg, null);
    }

    //endregion

    //region R.fail

    public static Result<?> fail() {
        return Result.fail(ResultTags.FAILED);
    }

    public static Result<?> fail(ResultTag tag) {
        return Result.of(tag.getStatus(), tag.getCode(), tag.getInfo(), null);
    }

    public static Result<?> fail(String msg) {
        return Result.of(ResultTags.FAILED.getStatus(), ResultTags.FAILED.getCode(), msg, null);
    }

    //endregion

    public Result<T> with(Object exts) {
        this.exts = exts;
        return this;
    }

    public boolean succeed() {
        return Objects.equals(this.code, ResultTags.SUCCEEDED.getCode());
    }

    //region ...

    @Override
    public String toString() {
        return "R{code=" + code + ", msg='" + info + "', data=" + data + ", exts=" + exts + '}';
    }

    //endregion
}