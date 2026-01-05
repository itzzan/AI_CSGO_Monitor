package com.zan.csgo.handle;

import com.zan.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @Author Zan
 * @Create 2026/1/5 15:18
 * @ClassName: GlobalExceptionHandler
 * @Description : 全局异常处理
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public Result<Object> handleException(Exception e) {
        log.error("系统异常: {}", e.getMessage(), e);
        return Result.failed(500, "系统异常: " + e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Object> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return Result.failed(400, "参数错误: " + e.getMessage());
    }
}
