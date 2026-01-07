package com.zan.csgo.crawler.strategy;

import com.zan.csgo.model.dto.PriceFetchResultDTO;

/**
 * @Author Zan
 * @Create 2026/1/7 09:38
 * @ClassName: MarketStrategy
 * @Description : 市场策略接口（所有平台的抓取类都必须实现这个接口）
 */
public interface MarketStrategy {

    /**
     * 获取价格
     * @param key 关键参数 (Steam、Buff传HashName)
     * @return 统一结果对象
     */
    PriceFetchResultDTO fetchPrice(Object key);

    /**
     * 获取平台名称 (用于日志)
     */
    String getPlatformName();
}
