package com.zan.common;

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

    /**
     * 1*.**  认证授权
     * 10.**  认证操作相关错误
     * 11.**  认证错误，配置及Token相关信息错误
     * 12.**  授权错误。权限配置相关错误
     */
    BUSINESS_ERROR("-1", "ERROR"),
    UNAUTHORIZED("1000", "你暂无权限使用该功能，请联系管理员。"),
    INVALID_TOKEN("1101", "无法解析的Token，也许Token已经失效"),
    INVALID_GRANT("1102", "账号或者密码错误！"),
    INVALID_SCOPE("1103", "授权范围错误"),
    INVALID_CLIENT("1104", "非法的客户端"),

    ACCESS_DENIED("1201", "拒绝访问"),
    ACCESS_DENIED_AUTHORITY_LIMITED("1202", "权限不足，拒绝访问"),

    /**
     * 2*.** 成功
     */
    SUCCESS("0", "成功"),
    SESSION_INVALID("1", "用户信息验证失败，请重新登录"),
    REPEAT_ERROR("2", "请勿重复操作"),
    SUCCEED("200", "success"),

    /**
     * 4*.** Java常规错误
     */
    FAIL("4000", "失败"),
    WARNING("4001", "警告"),

    METHOD_NOT_ALLOWED("4105", "请求方法不支持"),
    PARAM_ERROR("4106", "参数异常"),

    /**
     * 6*.* 为数据操作相关错误
     */
    BAD_SQL_GRAMMAR("6000", "低级SQL语法错误，检查SQL能否正常运行或者字段名称是否正确"),
    DATA_INTEGRITY_VIOLATION("6200", "该数据正在被其它数据引用，请先删除引用关系，再进行数据删除操作"),

    /**
     * 7*.* 基础设施交互错误
     * 71.* Redis 操作出现错误
     */
    PIPELINE_INVALID_COMMANDS("7100", "Redis管道包含一个或多个无效命令"),

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

    /**
     * 账号错误
     */
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