package com.zan.csgo.core.api;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zan.csgo.core.model.SkinPriceRecordEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * @Author Zan
 * @Create 2026/1/5 11:57
 * @ClassName: SteamApiClient
 * @Description : Steam API客户端
 */
@Component
@Slf4j
public class SteamApiClient extends BaseApiClient {

    private static final String STEAM_COMMUNITY_URL = "https://steamcommunity.com";
    private static final String STEAM_API_URL = "https://api.steampowered.com";

    /**
     * 获取Steam市场物品价格
     */
    public SkinPriceRecordEntity getMarketPrice(String marketHashName, String currency, String appId) {
        try {
            String url = STEAM_COMMUNITY_URL + "/market/priceoverview/";

            Map<String, Object> params = new HashMap<>();
            params.put("country", "CN");
            params.put("currency", currency);
            params.put("appid", appId); // 730 for CS:GO
            params.put("market_hash_name", URLEncoder.encode(marketHashName, StandardCharsets.UTF_8));

            HttpResponse response = HttpRequest.get(url)
                    .form(params)
                    .timeout(10000)
                    .execute();

            if (response.isOk()) {
                String body = response.body();
                JSONObject json = JSONUtil.parseObj(body);

                if (json.getBool("success", false)) {
                    return convertToPriceRecord(marketHashName, json);
                }
            }
        } catch (Exception e) {
            log.error("获取Steam价格失败: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * 获取Steam市场历史价格
     */
    public List<SkinPriceRecordEntity> getMarketPriceHistory(String marketHashName, String currency, String appId) {
        List<SkinPriceRecordEntity> records = new ArrayList<>();
        try {
            String url = STEAM_COMMUNITY_URL + "/market/pricehistory/";

            Map<String, Object> params = new HashMap<>();
            params.put("country", "CN");
            params.put("currency", currency);
            params.put("appid", appId);
            params.put("market_hash_name", URLEncoder.encode(marketHashName, StandardCharsets.UTF_8));

            HttpResponse response = HttpRequest.get(url)
                    .form(params)
                    .timeout(10000)
                    .execute();

            if (response.isOk()) {
                String body = response.body();
                JSONObject json = JSONUtil.parseObj(body);

                if (json.getBool("success", false)) {
                    JSONArray prices = json.getJSONArray("prices");
                    for (Object priceObj : prices) {
                        JSONArray priceArray = (JSONArray) priceObj;
                        if (priceArray.size() >= 3) {
                            SkinPriceRecordEntity record = new SkinPriceRecordEntity();
                            record.setSkinMarketHashName(marketHashName);
                            record.setPlatform("Steam");
                            record.setRecordTime(new Date(priceArray.getLong(0) * 1000));
                            record.setCurrentPrice(BigDecimal.valueOf(priceArray.getDouble(1)));
                            record.setVolume24h(priceArray.getInt(2));
                            records.add(record);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取Steam历史价格失败: {}", e.getMessage(), e);
        }
        return records;
    }

    /**
     * 批量获取价格
     */
    public Map<String, SkinPriceRecordEntity> batchGetPrices(Set<String> marketHashNames, String currency) {
        Map<String, SkinPriceRecordEntity> result = new HashMap<>();

        // Steam没有批量API，需要逐个获取
        for (String marketHashName : marketHashNames) {
            try {
                SkinPriceRecordEntity record = getMarketPrice(marketHashName, currency, "730");
                if (record != null) {
                    result.put(marketHashName, record);
                }
                // 避免请求过快
                Thread.sleep(100);
            } catch (Exception e) {
                log.error("批量获取价格失败 - {}: {}", marketHashName, e.getMessage());
            }
        }

        return result;
    }

    /**
     * 获取Steam库存物品列表
     */
    public JSONObject getInventoryItems(String steamId, String appId) {
        try {
            String url = STEAM_API_URL + "/IEconItems_" + appId + "/GetPlayerItems/v1/";

            Map<String, Object> params = new HashMap<>();
            params.put("key", apiConfig.getPlatforms().get("steam").getApiKey());
            params.put("SteamID", steamId);

            HttpResponse response = HttpRequest.get(url)
                    .form(params)
                    .timeout(15000)
                    .execute();

            if (response.isOk()) {
                return JSONUtil.parseObj(response.body());
            }
        } catch (Exception e) {
            log.error("获取Steam库存失败: {}", e.getMessage(), e);
        }
        return null;
    }

    private SkinPriceRecordEntity convertToPriceRecord(String marketHashName, JSONObject json) {
        SkinPriceRecordEntity record = new SkinPriceRecordEntity();
        record.setSkinMarketHashName(marketHashName);
        record.setPlatform("Steam");
        record.setRecordTime(new Date());

        String lowestPrice = json.getStr("lowest_price");
        String medianPrice = json.getStr("median_price");
        String volume = json.getStr("volume");

        if (StrUtil.isNotBlank(lowestPrice)) {
            record.setLowestPrice(parseSteamPrice(lowestPrice));
        }

        if (StrUtil.isNotBlank(medianPrice)) {
            record.setCurrentPrice(parseSteamPrice(medianPrice));
        }

        if (StrUtil.isNotBlank(volume)) {
            record.setVolume24h(Integer.parseInt(volume.replace(",", "")));
        }

        return record;
    }

    private BigDecimal parseSteamPrice(String priceStr) {
        try {
            // 移除货币符号和空格
            String cleaned = priceStr.replaceAll("[^\\d.,]", "").replace(",", "");
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
