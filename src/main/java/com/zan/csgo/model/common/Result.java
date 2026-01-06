package com.zan.csgo.model.common;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * @Author Zan
 * @Create 2026/1/5 11:31
 * @ClassName: Result
 * @Description : 统一响应实体
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 0表示操作成功，否则表示操作失败
     */
    private String code = "0";

    /**
     * 操作成功或失败后的提示信息
     */
    private String msg;

    /**
     * 数据内容
     */
    private T data;

    private Throwable error;

    private boolean success;

    /**
     * 响应时间戳
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date timestamp = new Date();

    public Result(String code, String msg, T data, boolean success) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.success = success;
    }

    public static <T> Result<T> success(IBaseResult resultStatus) {
        return of(null, resultStatus.getCode(), resultStatus.getMessage(), true);
    }

    public static <T> Result<T> success(String msg) {
        return of(null, ResultStatus.SUCCESS.getCode(), msg, true);
    }

    public static <T> Result<T> success(T model, String msg) {
        return of(model, ResultStatus.SUCCESS.getCode(), msg, true);
    }

    public static <T> Result<T> success(T model) {
        return of(model, ResultStatus.SUCCESS.getCode(), "", true);
    }

    public static <T> Result<T> success() {
        return of(null, ResultStatus.SUCCESS.getCode(), ResultStatus.SUCCESS.getMessage(), true);
    }

    public static <T> Result<T> success(T model, String code, String msg) {
        return of(model, code, msg, true);
    }

    public static <T> Result<T> failed(String msg) {
        return of(null, ResultStatus.ERROR.getCode(), msg, false);
    }

    public static <T> Result<T> failed(IBaseResult resultStatus) {
        return of(null, resultStatus.getCode(), resultStatus.getMessage(), false);
    }

    public static <T> Result<T> failed(T model, String msg) {
        return of(model, ResultStatus.ERROR.getCode(), msg, false);
    }

    public static <T> Result<T> failed(T model) {
        return of(model, ResultStatus.ERROR.getCode(), "", false);
    }

    public static <T> Result<T> of(T datas, String code, String msg, boolean success) {
        return new Result<>(code, msg, datas, success);
    }

    public Result<T> code(String code) {
        this.code = code;
        return this;
    }

    public Result<T> message(String msg) {
        this.msg = msg;
        return this;
    }

    public Result<T> data(T data) {
        this.data = data;
        return this;
    }

    public Result<T> type(ResultStatus resultStatus) {
        this.code = resultStatus.getCode();
        this.msg = resultStatus.getMessage();
        return this;
    }

    public Result<T> error(Throwable error) {
        this.error = error;
        return this;
    }

    public boolean isSuccess() {
        return success;
    }

}
