package com.zan.csgo.crawler.strategy.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zan.csgo.crawler.strategy.MarketStrategy;
import com.zan.csgo.model.dto.PriceFetchResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * @Author Zan
 * @Create 2026/1/7 09:39
 * @ClassName: SteamStrategy
 * @Description : Steam 抓取策略
 */
@Component
@Slf4j
public class SteamStrategy implements MarketStrategy {

    private static final String STEAM_API_URL = "https://steamcommunity.com/market/priceoverview/?appid=730&currency=23&market_hash_name=";

    @Override
    public String getPlatformName() {
        return "STEAM";
    }

    @Override
    public PriceFetchResultDTO fetchPrice(Object key) {
        String marketHashName = (String) key; // Steam 需要 String 类型的 HashName

        log.info(">>> 开始抓取 Steam: {}", marketHashName);

        // 构造 URL (注意: currency=23 代表人民币)
        String url = STEAM_API_URL + HttpUtil.encodeParams(marketHashName, null);

        try {
            // 发起请求
            // 【注意】如果你的服务器在国内，这里通常需要配置代理，否则会超时
            // HttpRequest.get(url).setHttpProxy("127.0.0.1", 7890).timeout(5000)...
            String res = HttpUtil.get(url, 5000);

            // 检查响应内容
            if (StrUtil.isBlank(res)) {
                return PriceFetchResultDTO.fail("STEAM", "响应为空");
            }

            JSONObject json = JSONUtil.parseObj(res);

            // Steam 成功标志: success 为 true
            if (json.getBool("success") != null && json.getBool("success")) {

                // 1. 解析最低价 (lowest_price)
                // 格式可能是: "¥ 138.50" 或 "$ 19.99"
                String priceStr = json.getStr("lowest_price");
                BigDecimal price = null;

                if (StrUtil.isNotBlank(priceStr)) {
                    // 清洗数据: 去掉 '¥', '$', ',' 等非数字字符，只保留数字和小数点
                    // 正则表达式: [^0-9.] 表示匹配所有非数字和非小数点的字符
                    String cleanPrice = priceStr.replaceAll("[^0-9.]", "").trim();
                    if (StrUtil.isNotBlank(cleanPrice)) {
                        price = new BigDecimal(cleanPrice);
                    }
                }

                // 2. 解析销量 (volume)
                // 格式: "1,204" (带逗号)
                String volumeStr = json.getStr("volume");
                int volume = 0;

                if (StrUtil.isNotBlank(volumeStr)) {
                    String cleanVolume = volumeStr.replace(",", "").trim();
                    try {
                        volume = Integer.parseInt(cleanVolume);
                    } catch (NumberFormatException e) {
                        // 偶尔返回非数字，忽略
                    }
                }

                // 校验: 如果没价格，视为抓取失败（可能没人卖）
                if (price == null) {
                    return PriceFetchResultDTO.fail("STEAM", "暂无市场售价");
                }

                log.info("✅ Steam抓取成功: {} -> 价格: ¥{}, 销量: {}", marketHashName, price, volume);

                // 返回成功 DTO
                return PriceFetchResultDTO.builder()
                        .success(true)
                        .platform("STEAM")
                        .price(price)
                        .volume(volume)
                        .build();
            } else {
                // success 为 false 或者 null
                return PriceFetchResultDTO.fail("STEAM", "API返回失败 (可能物品不存在)");
            }

        } catch (Exception e) {
            log.error("Steam 请求异常: {}", marketHashName, e);
            return PriceFetchResultDTO.fail("STEAM", "系统异常: " + e.getMessage());
        }
    }
}
