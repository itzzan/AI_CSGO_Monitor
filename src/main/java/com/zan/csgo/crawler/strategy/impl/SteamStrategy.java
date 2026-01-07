package com.zan.csgo.crawler.strategy.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
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

        // 1. 构造 URL (必须进行 URL 编码)
        String url = String.format(steamSearchApiUrl, HttpUtil.encodeParams(marketHashName, null));

        log.info(">>> 开始抓取 Steam (Render): {}", marketHashName);

        try {
            // 2. 发起请求 (强烈建议使用 HttpRequest 以便设置 Header 和 代理)
            HttpRequest request = HttpRequest.get(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept-Language", "zh-CN,zh;q=0.9") // 强制中文
                    .timeout(10000); // Steam 响应较慢，超时设长一点

            String res = request.execute().body();

            // 3. 校验响应
            if (StrUtil.isBlank(res)) {
                return PriceFetchResultDTO.fail("STEAM", "接口无响应");
            }

            JSONObject json = JSONUtil.parseObj(res);

            // 校验 success
            if (json.getBool("success") == null || !json.getBool("success")) {
                // 有时候 Steam 即使 429 也返回 JSON，但 success 是 false
                return PriceFetchResultDTO.fail("STEAM", "API返回失败 (可能限流或物品不存在)");
            }

            // ==========================================
            // 4. 获取核心数据: 总在售数量 (Total Count)
            // ==========================================
            Integer totalCount = json.getInt("total_count");
            if (totalCount == null) totalCount = 0;

            // ==========================================
            // 5. 获取核心数据: 最低价 (Price)
            // ==========================================
            BigDecimal price = null;

            // listinginfo 是一个 Map，Key 是 ListingID，Value 是详情
            JSONObject listingInfoMap = json.getJSONObject("listinginfo");

            if (ObjectUtil.isNotNull(listingInfoMap)) {
                // 因为我们只请求了 count=1，所以 Map 里通常只有 1 个 Key (即最低价的那个单子)
                // 我们直接取 map 的第一个 value
                for (String listingId : listingInfoMap.keySet()) {
                    JSONObject listing = listingInfoMap.getJSONObject(listingId);

                    // Steam 返回的价格是整数（分），并且包含两部分：
                    // converted_price: 卖家到手价
                    // converted_fee: 手续费
                    // 买家实际支付 = price + fee
                    Long convertedPrice = listing.getLong("converted_price");
                    Long convertedFee = listing.getLong("converted_fee");

                    if (convertedPrice != null && convertedFee != null) {
                        long totalPriceInCents = convertedPrice + convertedFee;
                        // 除以 100 转为元
                        price = NumberUtil.div(new BigDecimal(totalPriceInCents), new BigDecimal(100), 2, RoundingMode.HALF_UP);
                    }

                    // 只要取到第一个（最低价）就跳出
                    break;
                }
            }

            if (price == null) {
                return PriceFetchResultDTO.fail("STEAM", "暂无挂单");
            }

            log.info("✅ Steam抓取成功: {} -> 价格: ¥{}, 在售总数: {}", marketHashName, price, totalCount);

            // 6. 返回结果
            return PriceFetchResultDTO.builder()
                    .success(true)
                    .platform("STEAM")
                    .price(price)
                    .volume(totalCount) // 这里把 total_count 赋给 volume 字段 (注意：Steam Render 接口不返回 24h销量)
                    .targetId(null)
                    .build();

        } catch (cn.hutool.core.io.IORuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("429")) {
                log.error("❌ Steam 触发限流 (429 Too Many Requests)");
                return PriceFetchResultDTO.fail("STEAM", "触发限流(429)");
            }
            log.error("Steam 网络异常", e);
            return PriceFetchResultDTO.fail("STEAM", "网络超时");
        } catch (Exception e) {
            log.error("Steam 解析异常: {}", marketHashName, e);
            return PriceFetchResultDTO.fail("STEAM", "系统异常");
        }
    }
}
