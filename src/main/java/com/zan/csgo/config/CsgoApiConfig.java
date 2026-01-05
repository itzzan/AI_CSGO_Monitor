package com.zan.csgo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @Author Zan
 * @Create 2026/1/5 11:55
 * @ClassName: CsgoApiConfig
 * @Description : CSGO API 配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "csgo.api")
public class CsgoApiConfig {

    private Map<String, PlatformConfig> platforms;

    @Data
    public static class PlatformConfig {

        /**
         * 平台基础URL
         */
        private String baseUrl;

        /**
         * 平台API密钥
         */
        private String apiKey;

        /**
         * 请求超时时间（毫秒）
         */
        private Integer timeout;

        /**
         * 重试次数
         */
        private Integer retryTimes;

        /**
         * 是否启用
         */
        private Boolean enabled;

        /**
         * 各个API的Endpoint
         */
        private Map<String, String> endpoints;
    }
}
