package com.zan.csgo.crawler.strategy;

import com.zan.csgo.model.dto.PriceFetchResultDTO;

import java.util.List;

/**
 * @Author Zan
 * @Create 2026/1/7 09:38
 * @ClassName: MarketStrategy
 * @Description : 市场策略接口（所有平台的抓取类都必须实现这个接口）
 */
public interface MarketStrategy {

    /**
     * 获取平台名称 (用于日志)
     */
    String getPlatformName();

    /**
     * 获取价格
     *
     * @param key 关键参数 (Steam、Buff传HashName)
     * @return 统一结果对象
     */
    PriceFetchResultDTO fetchPrice(Object key);

    /**
     * 批量获取价格
     *
     * @param ids 平台对应的商品ID列表 (String类型，兼容长整型和字符串)
     * @return 抓取成功的价格列表
     */
    List<PriceFetchResultDTO> batchFetchPrices(List<String> ids);
}
