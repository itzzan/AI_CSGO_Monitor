package com.zan.csgo.crawler.strategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author Zan
 * @Create 2026/1/7 10:25
 * @ClassName: MarketStrategyFactory
 * @Description : 策略工厂：负责管理和分发具体的策略
 */
@Component
public class MarketStrategyFactory {

    // 核心：Spring 会自动把所有 MarketStrategy 的实现类注入进来
    // Key 是 Bean 的名字 (默认是类名首字母小写，如 "buffStrategy")
    // 或者我们自己在策略类里通过 getPlatformName() 来注册
    private final Map<String, MarketStrategy> strategyMap = new ConcurrentHashMap<>();

    /**
     * 构造函数注入：利用 Spring 自动收集所有实现类
     */
    @Autowired
    public MarketStrategyFactory(Map<String, MarketStrategy> strategyBeans) {
        // 我们重新整理一下 Map，用 getPlatformName() (比如 "BUFF", "STEAM") 作为 Key
        // 这样获取的时候更直观
        strategyBeans.forEach((k, v) -> {
            strategyMap.put(v.getPlatformName().toUpperCase(), v);
        });
    }

    /**
     * 对外方法：根据平台名称获取策略
     */
    public MarketStrategy getStrategy(String platform) {
        MarketStrategy strategy = strategyMap.get(platform.toUpperCase());
        if (strategy == null) {
            throw new IllegalArgumentException("未找到该平台的策略实现: " + platform);
        }
        return strategy;
    }
}
