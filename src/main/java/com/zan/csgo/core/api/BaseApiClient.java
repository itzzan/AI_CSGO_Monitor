package com.zan.csgo.core.api;

import com.zan.csgo.config.CsgoApiConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.DigestUtils;

import java.util.Map;

/**
 * @Author Zan
 * @Create 2026/1/5 14:00
 * @ClassName: BaseApiClient
 * @Description : 基本Api客户端
 */
@Slf4j
public abstract class BaseApiClient {

    @Resource
    protected CsgoApiConfig apiConfig;

    /**
     * 获取平台配置
     */
    protected CsgoApiConfig.PlatformConfig getPlatformConfig(String platform) {
        Map<String, CsgoApiConfig.PlatformConfig> platforms = apiConfig.getPlatforms();
        return platforms.getOrDefault(platform, new CsgoApiConfig.PlatformConfig());
    }

    /**
     * 生成签名
     */
    protected String generateSignature(Map<String, Object> params, String secretKey) {
        // 生成API签名的通用方法
        StringBuilder sb = new StringBuilder();
        params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sb.append(entry.getKey()).append("=").append(entry.getValue()).append("&"));

        if (!sb.isEmpty()) {
            sb.deleteCharAt(sb.length() - 1);
        }

        sb.append(secretKey);
        return DigestUtils.md5DigestAsHex(sb.toString().getBytes());
    }
}
