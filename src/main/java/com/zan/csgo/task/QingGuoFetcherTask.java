package com.zan.csgo.task;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @Author Zan
 * @Create 2026/1/8 11:54
 * @ClassName: QingGuoFetcherTask
 * @Description : 青果代理搬运工
 *                作用：定时去青果 API 进货，放到 Redis 里给爬虫用
 */
@Component
@Slf4j
public class QingGuoFetcherTask {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 填入你在青果后台生成的 API 链接
    private static final String API_URL = "https://share.proxy.qg.net/get?key=118B1E3B&count=3&type=1&format=txt";

    // Redis Key 保持和你 ProxyProvider 里的一致
    private static final String REDIS_KEY = "use_proxy";

    /**
     * 每 10 秒进货一次 (根据青果 IP 的有效期调整)
     * 假设青果 IP 有效期是 3~5 分钟，我们 10 秒拿一次新的补充进去
     */
    @Scheduled(fixedDelay = 10000)
    public void fetchProxies() {
        log.info("🚚 [搬运工] 开始去青果进货...");

        try {
            // 1. 调用 API 获取 IP 列表
            String result = HttpUtil.get(API_URL);

            if (StrUtil.isBlank(result) || result.contains("{")) {
                // 如果返回 JSON (如 {"code": "failed"...}) 说明出错了，比如白名单没加，或者频率太快
                log.warn("⚠️ [搬运工] 进货失败: {}", result);
                return;
            }

            // 2. 解析结果 (青果默认是换行符分隔)
            String[] proxies = result.split("\r\n");

            for (String proxy : proxies) {
                if (StrUtil.isBlank(proxy)) {
                    continue;
                }

                // proxy 格式通常是: 123.45.67.89:8888
                // 3. 存入 Redis
                // 注意：这里我们换一种存法，为了方便自动过期，不用 Hash 了，改用 Set 或者直接由业务维护
                // 但为了兼容你之前的 ProxyProvider (Hash结构)，我们这样做：

                stringRedisTemplate.opsForHash().put(REDIS_KEY, proxy, System.currentTimeMillis() + "");

                log.info("✨ [搬运工] 新货上架: {}", proxy);
            }

        } catch (Exception e) {
            log.error("❌ [搬运工] 网络异常", e);
        }
    }
}
