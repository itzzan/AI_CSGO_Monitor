package com.zan.csgo.service;

import java.math.BigDecimal;

/**
 * @Author Zan
 * @Create 2026/1/7 16:37
 * @ClassName: INotificationService
 * @Description : 消息提醒服务
 */
public interface INotificationService {

    /**
     * 发送价格异动报警
     *
     * @param skinName    饰品名称
     * @param platform   平台名称
     * @param oldPrice   1分钟前价格
     * @param newPrice   最新价格
     * @param changeRate 涨跌幅
     */
    void sendPriceAlert(String skinName, String platform, BigDecimal oldPrice, BigDecimal newPrice, String changeRate);
}
