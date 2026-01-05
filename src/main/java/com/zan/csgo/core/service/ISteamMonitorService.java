package com.zan.csgo.core.service;

import com.zan.csgo.core.model.SkinPriceRecordEntity;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @Author Zan
 * @Create 2026/1/5 15:04
 * @ClassName: ISteamMonitorService
 * @Description : TODO 请用一句话描述一下
 */
public interface ISteamMonitorService {

    /**
     * 开始监控Steam饰品
     */
    void startMonitoring();

    /**
     * 停止监控
     */
    void stopMonitoring();

    /**
     * 监控指定饰品
     */
    SkinPriceRecordEntity monitorSkin(String marketHashName);

    /**
     * 批量监控饰品
     */
    Map<String, SkinPriceRecordEntity> batchMonitorSkins(List<String> marketHashNames);

    /**
     * 获取饰品最新价格
     */
    SkinPriceRecordEntity getLatestPrice(String marketHashName);

    /**
     * 获取价格历史
     */
    List<SkinPriceRecordEntity> getPriceHistory(String marketHashName, Date startTime, Date endTime);

    /**
     * 检测价格异常
     */
    List<SkinPriceRecordEntity> detectPriceAnomalies(BigDecimal threshold);

    /**
     * 检测库存异常
     */
    List<SkinPriceRecordEntity> detectInventoryAnomalies(Integer threshold);

    /**
     * 计算价格趋势
     */
    Map<String, Object> calculatePriceTrend(String marketHashName, Integer days);

    /**
     * 获取监控统计
     */
    Map<String, Object> getMonitoringStats();
}
