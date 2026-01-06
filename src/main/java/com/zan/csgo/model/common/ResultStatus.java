package com.zan.csgo.model.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author Zan
 * @Create 2026/1/5 11:35
 * @ClassName: ResultStatus
 * @Description : 统一结果状态码
 */
@AllArgsConstructor
@Getter
public enum ResultStatus implements IBaseResult {

    BUSINESS_ERROR("-1", "ERROR"),

    SUCCESS("0", "成功"),
    SUCCEED("200", "success"),

    INVALID_REQUEST("2002", "Invalid Request"),
    REDIRECT_URI_MISMATCH("2005", "Redirect Uri Mismatch"),
    UNAUTHORIZED_CLIENT("2006", "Unauthorized Client"),
    EXPIRED_TOKEN("2007", "Expired Token"),
    UNSUPPORTED_GRANT_TYPE("2008", "Unsupported Grant Type"),
    UNSUPPORTED_RESPONSE_TYPE("2009", "Unsupported Response Type"),
    UNSUPPORTED_MEDIA_TYPE("2010", "Unsupported Media Type"),
    SIGNATURE_DENIED("2013", "Signature Denied"),

    ACCESS_DENIED_BLACK_IP_LIMITED("4031", "Access Denied Black Ip Limited"),
    ACCESS_DENIED_WHITE_IP_LIMITED("4032", "Access Denied White Ip Limited"),
    ACCESS_DENIED_UPDATING("4034", "Access Denied Updating"),
    ACCESS_DENIED_DISABLED("4035", "Access Denied Disabled"),
    ACCESS_DENIED_NOT_OPEN("4036", "Access Denied Not Open"),

    BAD_CREDENTIALS("3000", "Bad Credentials"),
    ACCOUNT_DISABLED("3001", "Account Disabled"),
    ACCOUNT_EXPIRED("3002", "Account Expired"),
    CREDENTIALS_EXPIRED("3003", "Credentials Expired"),
    ACCOUNT_LOCKED("3004", "Account Locked"),
    USERNAME_NOT_FOUND("3005", "Username Not Found"),
    USER_IS_DISABLED("3006", "User is disabled"),

    /**
     * 请求错误
     */
    BAD_REQUEST("4100", "Bad Request"),
    NOT_FOUND("4104", "Not Found"),
    MEDIA_TYPE_NOT_ACCEPTABLE("4106", "Media Type Not Acceptable"),
    TOO_MANY_REQUESTS("4129", "Too Many Requests"),

    /**
     * 系统错误
     */
    ERROR("5220", "Error"),
    GATEWAY_TIMEOUT("5004", "Gateway Timeout"),
    SERVICE_UNAVAILABLE("5003", "Service Unavailable");

    /**
     * 结果代码
     */
    private final String code;

    /**
     * 结果信息
     */
    private final String message;

    public static ResultStatus getResultEnumByCode(String code) {
        for (ResultStatus type : ResultStatus.values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        return ERROR;
    }

    public static ResultStatus getResultEnumByMessage(String message) {
        for (ResultStatus type : ResultStatus.values()) {
            if (type.getMessage().equals(message)) {
                return type;
            }
        }
        return ERROR;
    }

    public static boolean isFailed(IBaseResult resultStatus) {
        return !isSuccess(resultStatus);
    }

    public static boolean isSuccess(IBaseResult resultStatus) {
        return SUCCESS.code.equalsIgnoreCase(resultStatus.getCode());
    }
}