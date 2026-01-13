package com.zan.csgo.crawler.strategy.impl;

import com.zan.csgo.crawler.strategy.MarketStrategy;
import com.zan.csgo.enums.PlatformEnum;
import com.zan.csgo.model.dto.PriceFetchResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author Zan
 * @Create 2026/1/13 10:28
 * @ClassName: YoupinStrategy
 * @Description : 悠悠有品抓取策略
 */
@Component
@Slf4j
public class YoupinStrategy implements MarketStrategy {

    @Override
    public String getPlatformName() {
        return PlatformEnum.YOUPIN.getName();
    }

    @Override
    public PriceFetchResultDTO fetchPrice(Object key) {
        return null;
    }

    @Override
    public List<PriceFetchResultDTO> batchFetchPrices(List<String> ids) {
        return List.of();
    }
}
