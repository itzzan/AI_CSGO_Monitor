package com.zan.common;

/**
 * @Author Zan
 * @Create 2026/1/5 11:34
 * @ClassName: IBaseResult
 * @Description : 基础结果接口
 */
public interface IBaseResult {

    /**
     * 返回状态码
     *
     * @return
     */
    String getCode();

    /**
     * 获取返回信息
     *
     * @return
     */
    String getMessage();
}
