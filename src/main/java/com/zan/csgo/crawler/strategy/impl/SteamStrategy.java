package com.zan.csgo.crawler.strategy.impl;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zan.csgo.crawler.strategy.MarketStrategy;
import com.zan.csgo.enums.PlatformEnum;
import com.zan.csgo.model.dto.PriceFetchResultDTO;
import com.zan.csgo.utils.ProxyProviderUtil;
import com.zan.csgo.utils.UserAgentUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.Proxy;
import java.util.List;

/**
 * @Author Zan
 * @Create 2026/1/7 09:39
 * @ClassName: SteamStrategy
 * @Description : Steam 抓取策略
 */
@Component
@Slf4j
public class SteamStrategy implements MarketStrategy {

    @Value("${csgo.monitor.steam.price-api-url}")
    private String steamPriceApiUrl;

    @Value("${csgo.monitor.steam.search-api-url}")
    private String steamSearchApiUrl;

    @Resource
    private ProxyProviderUtil proxyProviderUtil;

    private static final int MAX_RETRIES = 5;

    @Override
    public String getPlatformName() {
        return PlatformEnum.STEAM.getName();
    }

    @Override
    public PriceFetchResultDTO fetchPrice(Object key) {
        String marketHashName = (String) key;
        // 构造 URL
        String url = String.format(steamSearchApiUrl, HttpUtil.encodeParams(marketHashName, null));
        long startTime = System.currentTimeMillis();

        log.info(">>> [Steam] 开始抓取: {}", marketHashName);

        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            attempt++;
            Proxy proxy = proxyProviderUtil.getRandomProxy();
            String proxyStr = (proxy != null) ? proxy.address().toString() : "直连(无代理)";

            try {
                HttpRequest request = HttpRequest.get(url)
                        .header("User-Agent", UserAgentUtil.random())
                        .header("Accept-Language", "zh-CN,zh;q=0.9")
                        .timeout(6000); // 6秒超时

                if (proxy != null) {
                    request.setProxy(proxy);
                }

                try (HttpResponse response = request.execute()) {
                    int status = response.getStatus();

                    // 1. 处理限流
                    if (status == 429) {
                        log.warn("⚠️ [Steam] 第{}次失败: 触发429限流 (Proxy: {})", attempt, proxyStr);
                        if (proxy != null) proxyProviderUtil.removeBadProxy(proxy);
                        continue;
                    }

                    // 2. 处理非200
                    if (status != 200) {
                        log.warn("⚠️ [Steam] 第{}次失败: HTTP状态码 {} (Proxy: {})", attempt, status, proxyStr);
                        if (proxy != null) proxyProviderUtil.removeBadProxy(proxy);
                        continue;
                    }

                    String res = response.body();

                    // 3. 防御性编程：检查是否为 JSON
                    if (StrUtil.isBlank(res) || !StrUtil.startWith(res.trim(), "{")) {
                        String preview = StrUtil.sub(res, 0, 100).replace("\n", "");
                        log.error("❌ [Steam] 返回内容非JSON (可能是HTML报错): {}... (Proxy: {})", preview, proxyStr);
                        if (proxy != null) proxyProviderUtil.removeBadProxy(proxy);
                        continue;
                    }

                    // 4. 解析数据
                    JSONObject json = JSONUtil.parseObj(res);
                    if (json.getBool("success") != null && json.getBool("success")) {
                        Integer totalCount = json.getInt("total_count", 0);
                        BigDecimal price = null;

                        // 解析列表
                        JSONObject listingInfo = json.getJSONObject("listinginfo");
                        if (ObjectUtil.isNotNull(listingInfo)) {
                            for (String id : listingInfo.keySet()) {
                                JSONObject item = listingInfo.getJSONObject(id);
                                long fee = item.getLong("converted_fee", 0L);
                                long p = item.getLong("converted_price", 0L);
                                // Steam价格单位是分，转为元
                                price = NumberUtil.div(new BigDecimal(p + fee), new BigDecimal(100), 2, RoundingMode.HALF_UP);
                                break; // 取第一个即可
                            }
                        }

                        if (price != null) {
                            long cost = System.currentTimeMillis() - startTime;
                            log.info("✅ [Steam] 抓取成功: {} -> ¥{} (耗时: {}ms)", marketHashName, price, cost);
                            return PriceFetchResultDTO.builder()
                                    .success(true)
                                    .platform(getPlatformName())
                                    .price(price)
                                    .volume(totalCount)
                                    .build();
                        }
                    } else {
                        log.warn("⚠️ [Steam] API返回 success=false (Proxy: {})", proxyStr);
                    }
                }
            } catch (Exception e) {
                log.warn("⚠️ [Steam] 第{}次连接超时/异常: {} (Proxy: {})", attempt, e.getMessage(), proxyStr);
                if (proxy != null) proxyProviderUtil.removeBadProxy(proxy);
            }
        }

        log.error("❌ [Steam] {} 重试 {} 次全部失败", marketHashName, MAX_RETRIES);
        return PriceFetchResultDTO.fail("STEAM", "重试耗尽/无可用代理");
    }

    @Override
    public List<PriceFetchResultDTO> batchFetchPrices(List<String> ids) {
        return List.of();
    }
}
