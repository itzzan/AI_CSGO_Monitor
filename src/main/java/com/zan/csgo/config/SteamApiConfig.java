package com.zan.csgo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * @Author Zan
 * @Create 2026/1/5 15:03
 * @ClassName: SteamApiConfig
 * @Description : TODO 请用一句话描述一下
 */
@Data
@Component
@ConfigurationProperties(prefix = "csgo.steam")
public class SteamApiConfig {

    private String appId = "730";
    private String currency = "CNY";
    private String country = "CN";
    private String language = "schinese";
    private Integer timeout = 10000;
    private Integer rateLimitDelay = 1000; // 请求间隔(毫秒)
    private Integer maxRetryTimes = 3;
    private Boolean enabled = true;

    private MonitorConfig monitor = new MonitorConfig();
    private AlertConfig alert = new AlertConfig();

    @Data
    public static class MonitorConfig {
        private String cron = "0 */5 * * * ?"; // 每5分钟执行
        private Integer maxSkinsPerRun = 100; // 每次最多监控的饰品数量
        private Boolean recordHistory = true;
    }

    @Data
    public static class AlertConfig {
        private BigDecimal priceChangeThreshold = new BigDecimal("10.0");
        private Integer inventoryChangeThreshold = 50;
    }
}
