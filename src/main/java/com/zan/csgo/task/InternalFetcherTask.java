package com.zan.csgo.task;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zan.csgo.constant.RedisKeyConstant;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @Author Zan
 * @Create 2026/1/8 11:54
 * @ClassName: InternalFetcherTask
 * @Description : 青果代理搬运工（国内搬运工）
 *                作用：定时去青果 API 进货，放到 Redis 里给爬虫用
 */
@Component
@Slf4j
public class InternalFetcherTask {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${csgo.qingguo.internal-api-url}")
    private String qingGuoApiUrl;

    @Value("${csgo.qingguo.internal-auth-key}")
    private String qingGuoAuthKey;

    @Value("${csgo.qingguo.internal-auth-pwd}")
    private String qingGuoAuthPwd;

    @PostConstruct
    public void init() {
        log.info("🚀 [国内搬运工系统启动] 正在进行首次代理预热...");
        fetchProxies();
    }

    /**
     * 每 60 秒进货一次 (根据青果 IP 的有效期调整)
     * 假设青果 IP 有效期是 1~5 分钟，我们 60 秒拿一次新的补充进去
     */
    @Scheduled(fixedDelay = 1000 * 60)
    public void fetchProxies() {
        log.info("🚚 [国内搬运工] 开始去青果进货...");

        try {
            String apiUrl = String.format(qingGuoApiUrl, 5, qingGuoAuthKey, qingGuoAuthPwd);

            // 1. 请求 API
            String result = HttpUtil.get(apiUrl);

            // 简单防空检查
            if (StrUtil.isBlank(result)) {
                return;
            }

            // 2. 解析 JSON
            JSONObject json = JSONUtil.parseObj(result);

            // 3. 检查状态码 (根据你提供的 JSON，成功是 "SUCCESS")
            String code = json.getStr("code");
            if (!"SUCCESS".equals(code)) {
                log.warn("⚠️ [国内搬运工] 进货失败, 响应: {}", result);
                return;
            }

            // 4. 提取 Data 数组
            JSONArray data = json.getJSONArray("data");
            if (data == null || data.isEmpty()) {
                return;
            }

            int count = 0;
            for (int i = 0; i < data.size(); i++) {
                JSONObject item = data.getJSONObject(i);

                // 🔥 核心：取 'server' 字段 (格式如 222.139.246.31:20085)
                String proxyAddress = item.getStr("server");

                // 取过期时间 (deadline)，存入 Redis 的 Value 中，方便以后排查
                String deadline = item.getStr("deadline");

                if (StrUtil.isNotBlank(proxyAddress)) {
                    // 5. 存入 Redis Hash
                    // Key: useful_proxy
                    // Field: 222.139.246.31:20085 (作为唯一标识)
                    // Value: 2026-01-09 09:44:30 (过期时间)
                    stringRedisTemplate.opsForHash().put(RedisKeyConstant.PROXY_CN, proxyAddress, deadline);
                    count++;
                    log.info("🚚 [国内搬运工] 进货成功: {}", proxyAddress);
                }
            }

            if (count > 0) {
                log.info("🚚 [国内搬运工] 成功进货 {} 个代理 (模式: JSON)", count);
            }

        } catch (Exception e) {
            log.error("❌ [国内搬运工] 解析异常", e);
        }
    }
}
