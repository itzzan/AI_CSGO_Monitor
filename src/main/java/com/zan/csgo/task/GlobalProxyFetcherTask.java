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
 * @Create 2026/1/9 16:17
 * @ClassName: GlobalProxyFetcherTask
 * @Description : 海外代理搬运工 (专门给 Steam or 其他国外平台 用)
 */
@Component
@Slf4j
public class GlobalProxyFetcherTask {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 🔥 填入青果海外代理的 API 链接 (地区选不限或HK/US，记得加 &format=json)
    @Value("${csgo.qingguo.global-api-url}")
    private String qingGuoApiUrl;

    @Value("${csgo.qingguo.global-auth-key}")
    private String qingGuoAuthKey;

    @Value("${csgo.qingguo.global-auth-pwd}")
    private String qingGuoAuthPwd;

    @PostConstruct
    public void init() {
        log.info("🚀 [海外搬运工系统启动] 正在进行首次代理预热...");
        fetchProxies();
    }

    // 海外代理通常比较贵，频率可以低一点，比如 1 min 一次
    @Scheduled(fixedDelay = 1000 * 60)
    public void fetchProxies() {
        log.info("✈️ [海外搬运工] 开始去青果进货...");

        try {
            String apiUrl = String.format(qingGuoApiUrl, 5, qingGuoAuthKey, qingGuoAuthPwd);

            String result = HttpUtil.get(apiUrl);
            if (StrUtil.isBlank(result)) {
                return;
            }

            JSONObject json = JSONUtil.parseObj(result);
            if (!"SUCCESS".equals(json.getStr("code"))) {
                log.warn("⚠️ [海外搬运工] 进货失败: {}", result);
                return;
            }

            JSONArray data = json.getJSONArray("data");
            if (data == null || data.isEmpty()) return;

            int count = 0;
            for (int i = 0; i < data.size(); i++) {
                JSONObject item = data.getJSONObject(i);
                String proxy = item.getStr("server");
                String deadline = item.getStr("deadline"); // 海外代理通常有具体过期时间

                if (StrUtil.isNotBlank(proxy)) {
                    // 🔥 存入海外池
                    stringRedisTemplate.opsForHash().put(RedisKeyConstant.PROXY_GLOBAL, proxy, deadline);
                    count++;
                    log.info("✈️ [海外搬运工] 进货成功: {}", proxy);
                }
            }
            if (count > 0) {
                log.info("✈️ [海外搬运工] 进货成功: {} 个", count);
            }

        } catch (Exception e) {
            log.error("❌ [海外搬运工] 异常", e);
        }
    }
}
