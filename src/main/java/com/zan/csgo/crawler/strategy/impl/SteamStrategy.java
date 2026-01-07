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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

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

    @Override
    public String getPlatformName() {
        return PlatformEnum.STEAM.getName();
    }

    @Override
    public PriceFetchResultDTO fetchPrice(Object key) {
        String marketHashName = (String) key;
        String url = String.format(steamSearchApiUrl, HttpUtil.encodeParams(marketHashName, null));

        log.info(">>> 开始抓取 Steam (Render): {}", marketHashName);

        try {
            HttpRequest request = HttpRequest.get(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .timeout(10000);

            // 1. 获取完整响应对象 (不仅仅是 body)
            try (HttpResponse response = request.execute()) {

                // 2. 优先检查状态码
                int status = response.getStatus();
                if (status == 429) {
                    log.warn("❌ Steam 触发 429 限流: {}", marketHashName);
                    return PriceFetchResultDTO.fail("STEAM", "触发限流(429)");
                }
                if (status != 200) {
                    log.warn("❌ Steam 返回非200状态码: {} (饰品: {})", status, marketHashName);
                    return PriceFetchResultDTO.fail("STEAM", "HTTP状态码:" + status);
                }

                String res = response.body();

                // 3. 校验响应内容是否为空
                if (StrUtil.isBlank(res)) {
                    return PriceFetchResultDTO.fail("STEAM", "接口响应为空");
                }

                // 4. 🔥 核心修复：检查是否为 JSON 格式
                // 如果 Steam 返回 HTML (比如 502 Bad Gateway 或 封禁提示)，这里会拦截
                if (!StrUtil.startWith(res.trim(), "{")) {
                    // 截取前100个字符打印日志，看看到底返回了什么鬼东西
                    String preview = StrUtil.sub(res, 0, 200);
                    log.error("❌ Steam 返回了非 JSON 内容 (可能是HTML报错): {}", preview);
                    return PriceFetchResultDTO.fail("STEAM", "返回格式异常(非JSON)");
                }

                // 5. 安全解析 JSON
                JSONObject json = JSONUtil.parseObj(res);

                // 校验 success
                if (json.getBool("success") == null || !json.getBool("success")) {
                    return PriceFetchResultDTO.fail("STEAM", "API返回失败");
                }

                // ... 后续解析逻辑保持不变 ...
                Integer totalCount = json.getInt("total_count");
                if (totalCount == null) totalCount = 0;

                BigDecimal price = null;
                JSONObject listingInfoMap = json.getJSONObject("listinginfo");

                if (ObjectUtil.isNotNull(listingInfoMap)) {
                    for (String listingId : listingInfoMap.keySet()) {
                        JSONObject listing = listingInfoMap.getJSONObject(listingId);
                        Long convertedPrice = listing.getLong("converted_price");
                        Long convertedFee = listing.getLong("converted_fee");

                        if (convertedPrice != null && convertedFee != null) {
                            long totalPriceInCents = convertedPrice + convertedFee;
                            price = NumberUtil.div(new BigDecimal(totalPriceInCents), new BigDecimal(100), 2, RoundingMode.HALF_UP);
                        }
                        break;
                    }
                }

                if (price == null) {
                    return PriceFetchResultDTO.fail("STEAM", "暂无挂单");
                }

                log.info("✅ Steam抓取成功: {} -> 价格: ¥{}, 在售总数: {}", marketHashName, price, totalCount);

                return PriceFetchResultDTO.builder()
                        .success(true)
                        .platform("STEAM")
                        .price(price)
                        .volume(totalCount)
                        .targetId(null)
                        .build();
            }

        } catch (cn.hutool.core.io.IORuntimeException e) {
            // Hutool 在连接超时或 429 时可能会抛出此异常
            if (e.getMessage() != null && e.getMessage().contains("429")) {
                log.error("❌ Steam 触发限流 (429)");
                return PriceFetchResultDTO.fail("STEAM", "触发限流(429)");
            }
            log.error("Steam 网络异常: {}", e.getMessage());
            return PriceFetchResultDTO.fail("STEAM", "网络超时");
        } catch (Exception e) {
            log.error("Steam 解析异常: {}", marketHashName, e);
            return PriceFetchResultDTO.fail("STEAM", "系统异常");
        }
    }
}
