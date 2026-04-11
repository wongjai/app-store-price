package com.hypo.appstoreprice.pojo.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 自訂響應
 *
 * @author hypo
 * @date 2020-11-20 17:27
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class R {

    @Getter
    @AllArgsConstructor
    public enum Status {

        /**
         * 成功
         */
        SUCCESS(0, "成功"),

        /**
         * 失敗
         */
        FAIL(1, "失敗");


        private final Integer code;

        private final String desc;

    }

    /**
     * 響應狀態碼
     */
    private Integer code;

    /**
     * 響應訊息
     */
    private String message;

    /**
     * 響應中的數據
     */
    private Object data;


    /**
     * 0 成功 null
     */
    public static R ok() {
        return new R(Status.SUCCESS.getCode(), Status.SUCCESS.getDesc(), null);
    }

    /**
     * 0 成功 data
     */
    public static R ok(Object data) {
        return new R(Status.SUCCESS.getCode(), Status.SUCCESS.getDesc(), data);
    }

    /**
     * 0 message data
     */
    public static R ok(String message, Object data) {
        return new R(Status.SUCCESS.getCode(), message, data);
    }

    /**
     * 1 失敗 null
     */
    public static R failed() {
        return new R(Status.FAIL.getCode(), Status.FAIL.getDesc(), null);
    }

    /**
     * 1 失敗 msg
     */
    public static R failed(String msg) {
        return new R(Status.FAIL.getCode(), msg, null);
    }

    /**
     * 1 msg data
     */
    public static R failed(String msg, Object data) {
        return new R(Status.FAIL.getCode(), msg, data);
    }

}
