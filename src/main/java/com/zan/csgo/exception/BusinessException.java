package com.zan.csgo.exception;

import com.zan.csgo.model.common.IBaseResult;
import com.zan.csgo.model.common.ResultStatus;
import lombok.Getter;

import java.util.Objects;

/**
 * @Author Zan
 * @Create 2026/1/6 09:53
 * @ClassName: BusinessException
 * @Description : 业务异常
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    @Getter
    private String code;

    public BusinessException(String message) {
        super(message);
        this.code = ResultStatus.BUSINESS_ERROR.getCode();
    }

    public BusinessException(Exception e) {
        this(e.getMessage());
    }

    public BusinessException(IBaseResult baseResult) {
        this(baseResult.getCode(), baseResult.getMessage());
    }

    public BusinessException(String code, String message) {
        super(message);
        if (Objects.isNull(code)) {
            code = ResultStatus.ERROR.getCode();
        }
        this.code = code;
    }
}
