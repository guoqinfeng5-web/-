package com.smartlibrary.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一接口返回结构。
 *
 * @param <T> 数据载体类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    /** 200 表示成功；非 200 表示失败 */
    private int code;
    /** 提示信息 */
    private String message;
    /** 返回数据 */
    private T data;

    /**
     * 成功返回（无数据）。
     */
    public static <T> Result<T> ok() {
        return new Result<T>(200, "OK", null);
    }

    /**
     * 成功返回（带数据）。
     *
     * @param data 数据
     */
    public static <T> Result<T> ok(T data) {
        return new Result<T>(200, "OK", data);
    }

    /**
     * 失败返回（自定义消息）。
     *
     * @param message 错误信息
     */
    public static <T> Result<T> fail(String message) {
        return new Result<T>(-1, message, null);
    }

    /**
     * 失败返回（自定义 code + message）。
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<T>(code, message, null);
    }
}

