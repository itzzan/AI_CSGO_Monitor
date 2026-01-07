package com.zan.csgo.crawler.strategy.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
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
            // 兼容 Integer 转 Long (防止类型转换报错)
            if (key instanceof Integer) {
                key = ((Integer) key).longValue();
            } else {
                return PriceFetchResultDTO.fail(getPlatformName(), "无ID(请同步字典)");
            }
        }

        Long templateId = (Long) key;
        long startTime = System.currentTimeMillis();

        log.info(">>> 开始抓取 悠悠有品 价格 (ID: {})", templateId);

        try {
            // 2. 构造 Body
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("gameId", "730");
            paramMap.put("templateId", templateId.toString());
            paramMap.put("listType", "10");
            paramMap.put("listSortType", "1");
            paramMap.put("sortType", "0");
            paramMap.put("pageIndex", "1");
            paramMap.put("pageSize", "10");

            String jsonBody = JSONUtil.toJsonStr(paramMap);

            // 3. 发送请求
            HttpRequest request = HttpRequest.post(YouPinPriceApiUrl)
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
                    .timeout(8000);

            // 🔥 使用 HttpResponse 获取完整响应信息
            try (HttpResponse response = request.execute()) {

                // A. 检查状态码
                int status = response.getStatus();
                if (status != 200) {
                    log.warn("❌ [悠悠有品] HTTP状态异常 ID:{} Code:{}", templateId, status);
                    if (status == 429 || status == 403) {
                        return PriceFetchResultDTO.fail(getPlatformName(), "触发限流/WAF拦截 (" + status + ")");
                    }
                    return PriceFetchResultDTO.fail(getPlatformName(), "HTTP错误:" + status);
                }

                String res = response.body();

                // B. 检查响应是否为空
                if (StrUtil.isBlank(res)) {
                    return PriceFetchResultDTO.fail(getPlatformName(), "接口无响应");
                }

                // C. 🔥 核心防御：检查是否为 JSON 格式
                // 如果返回的是 <html>...</html>，这里直接拦截，防止报 JSONException
                if (!StrUtil.startWith(res.trim(), "{")) {
                    String preview = StrUtil.sub(res, 0, 200).replace("\n", "");
                    log.error("❌ [悠悠有品] 返回了 HTML 非 JSON (可能是被拦截): {}", preview);
                    return PriceFetchResultDTO.fail(getPlatformName(), "被拦截/返回HTML");
                }

                // 4. 解析 JSON
                JSONObject json = JSONUtil.parseObj(res);

                // 5. 业务 Code 校验
                Integer code = json.getInt("Code");
                if (code == null) code = json.getInt("code");

                if (code != null && code == 0) {
                    // 解析 Data
                    Object dataObj = json.get("Data");
                    if (dataObj == null) dataObj = json.get("data");

                    JSONArray items = null;
                    if (dataObj instanceof JSONArray) {
                        items = (JSONArray) dataObj;
                    } else {
                        // 如果 Data 是对象或其他，可能是详情页接口的数据结构，说明URL可能配错了，或者该ID没有挂单列表
                        log.warn("⚠️ [悠悠有品] ID:{} Data 类型不符: {}", templateId, dataObj != null ? dataObj.getClass().getSimpleName() : "null");
                    }

                    // 提取总数
                    Integer totalCount = json.getInt("TotalCount");
                    if (totalCount == null) totalCount = json.getInt("totalCount");

                    if (items != null && !items.isEmpty()) {
                        JSONObject cheapestItem = items.getJSONObject(0);
                        BigDecimal price = cheapestItem.getBigDecimal("price");
                        if (totalCount == null) totalCount = items.size();

                        long cost = System.currentTimeMillis() - startTime;
                        log.info("✅ [悠悠有品] 抓取成功 ID:{} -> ¥{} (在售:{}) 耗时:{}ms", templateId, price, totalCount, cost);

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
                    // 业务错误 (如 401 Token过期)
                    String msg = json.getStr("Msg");
                    if (msg == null) {
                        msg = json.getStr("msg");
                    }
                    log.error("❌ [悠悠有品] API业务错误 ID:{}, Msg:{}", templateId, msg);
                    return PriceFetchResultDTO.fail(getPlatformName(), "API拒绝:" + msg);
                }
            }

        } catch (Exception e) {
            log.error("❌ [悠悠有品] 系统异常 ID:" + templateId, e);
            return PriceFetchResultDTO.fail(getPlatformName(), "系统异常");
        }
    }
}
