package com.zan.csgo.crawler.strategy.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zan.csgo.crawler.strategy.MarketStrategy;
import com.zan.csgo.enums.PlatformEnum;
import com.zan.csgo.model.dto.PriceFetchResultDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author Zan
 * @Create 2026/1/7 11:33
 * @ClassName: YoupinStrategy
 * @Description : 悠悠抓取策略
 */
@Component
@Slf4j
public class YoupinStrategy implements MarketStrategy {

    @Value("${csgo.monitor.youpin.price-api-url}")
    private String YouPinPriceApiUrl;

    @Value("${csgo.monitor.youpin.authorization}")
    private String YouPinAuthorization;

    @Value("${csgo.monitor.youpin.deviceId}")
    private String YouPinDeviceId;

    @Value("${csgo.monitor.youpin.uk}")
    private String YouPinUk;

    @Value("${csgo.monitor.youpin.app-version}")
    private String YouPinAppVersion;

    @Override
    public String getPlatformName() {
        return PlatformEnum.YOUPIN.getName();
    }

    @Override
    public PriceFetchResultDTO fetchPrice(Object key) {
        // 1. 严格校验：只接受 ID
        if (!(key instanceof Long)) {
            return PriceFetchResultDTO.fail(getPlatformName(), "无ID(请同步字典)");
        }

        Long templateId = (Long) key;
        long startTime = System.currentTimeMillis();

        log.info(">>> 开始抓取 悠悠有品 价格 (ID: {})", templateId);

        try {
            // 2. 构造 Body (参考你的 curl data-raw)
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("gameId", "730");
            paramMap.put("templateId", templateId.toString());
            paramMap.put("listType", "10");     // 列表类型
            paramMap.put("listSortType", "1");  // 关键！1=价格升序，确保我们拿到的是最低价
            paramMap.put("sortType", "0");
            paramMap.put("pageIndex", "1");
            paramMap.put("pageSize", "10");     // 我们只需要最低价，取前10个够了

            String jsonBody = JSONUtil.toJsonStr(paramMap);

            // 3. 发送请求 (完整复刻浏览器 Header)
            String res = HttpRequest.post(YouPinPriceApiUrl)
                    .body(jsonBody)
                    // --- 核心鉴权 ---
                    .header("authorization", YouPinAuthorization)
                    .header("deviceId", YouPinDeviceId)
                    .header("uk", YouPinUk)
                    // --- 业务标识 ---
                    .header("App-Version", YouPinAppVersion)
                    .header("AppVersion", YouPinAppVersion)
                    .header("platform", "pc")
                    .header("appType", "1")
                    // --- 浏览器伪装 ---
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36")
                    .header("Origin", "https://youpin898.com")
                    .header("Referer", "https://youpin898.com/")
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("secret-v", "h5_v1")
                    .timeout(8000)
                    .execute().body();

            // 4. 响应校验
            if (StrUtil.isBlank(res)) {
                return PriceFetchResultDTO.fail(getPlatformName(), "接口无响应");
            }

            JSONObject json = JSONUtil.parseObj(res);

            // 5. 解析数据
            Integer code = json.getInt("Code");
            if (code == null) code = json.getInt("code"); // 兼容小写

            if (code != null && code == 0) {
                // ==========================================================
                // 核心解析逻辑 (基于你提供的 JSON)
                // ==========================================================

                // 2. 安全获取 Data 字段 (避免强转报错)
                Object dataObj = json.get("Data");
                if (dataObj == null) dataObj = json.get("data");

                JSONArray items = null;
                if (dataObj instanceof JSONArray) {
                    items = (JSONArray) dataObj; // 正常情况：Data 是数组
                } else {
                    // 异常情况：Data 可能是 null 或者是空对象 {}
                    log.warn("⚠️ [悠悠有品] ID:{} 返回的 Data 不是数组类型: {}", templateId, dataObj != null ? dataObj.getClass().getSimpleName() : "null");
                }

                // 3. 获取总数 (TotalCount 在根节点)
                Integer totalCount = json.getInt("TotalCount");
                if (totalCount == null) totalCount = json.getInt("totalCount");

                // 4. 提取价格
                if (items != null && !items.isEmpty()) {
                    // 取第一个 (因为 listSortType=1，所以是最低价)
                    JSONObject cheapestItem = items.getJSONObject(0);

                    // 价格字段 (JSON里是字符串 "31.47")
                    BigDecimal price = cheapestItem.getBigDecimal("price");

                    // 兜底总数
                    if (totalCount == null) totalCount = items.size();

                    long cost = System.currentTimeMillis() - startTime;
                    log.info("✅ [悠悠有品] 抓取成功 ID:{} -> 最低价: ¥{} (在售:{}) 耗时:{}ms",
                            templateId, price, totalCount, cost);

                    return PriceFetchResultDTO.builder()
                            .success(true)
                            .platform(getPlatformName())
                            .price(price)
                            .volume(totalCount)
                            .targetId(templateId.toString())
                            .build();
                } else {
                    return PriceFetchResultDTO.fail(getPlatformName(), "暂无在售");
                }

            } else {
                // 错误处理
                String msg = json.getStr("Msg");
                if (msg == null) msg = json.getStr("msg");

                if (code != null && code == 401) {
                    return PriceFetchResultDTO.fail(getPlatformName(), "Token过期");
                }
                log.error("❌ [悠悠有品] API错误 ID: {}, Msg: {}", templateId, msg);
                return PriceFetchResultDTO.fail(getPlatformName(), "API错误: " + msg);
            }

        } catch (Exception e) {
            log.error("❌ [悠悠有品] 系统异常 ID: " + templateId, e);
            return PriceFetchResultDTO.fail(getPlatformName(), "系统异常");
        }
    }
}
