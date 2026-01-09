package com.zan.csgo.crawler.strategy.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zan.csgo.crawler.strategy.MarketStrategy;
import com.zan.csgo.enums.PlatformEnum;
import com.zan.csgo.exception.BusinessException;
import com.zan.csgo.model.dto.PriceFetchResultDTO;
import com.zan.csgo.utils.ProxyProviderUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.Proxy;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    @Resource
    private ProxyProviderUtil proxyProviderUtil;

    private static final int MAX_RETRIES = 5;

    // 专用线程池，用于并发请求悠悠，避免阻塞主调度器
    private final ExecutorService youpinExecutor = Executors.newFixedThreadPool(10);

    @Override
    public String getPlatformName() {
        return PlatformEnum.YOUPIN.getName();
    }

    @Override
    public PriceFetchResultDTO fetchPrice(Object key) {
        // 1. 严格校验：只接受 ID
        if (!(key instanceof Long)) {
            if (key instanceof Integer) {
                key = ((Integer) key).longValue();
            } else {
                return PriceFetchResultDTO.fail(getPlatformName(), "无ID(请同步字典)");
            }
        }

        Long templateId = (Long) key;
        long startTime = System.currentTimeMillis();

        log.info(">>> [悠悠有品] 开始抓取 ID: {}", templateId);

        // 2. 构造 Body (提前构造好，避免循环里重复做)
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("gameId", "730");
        paramMap.put("templateId", templateId.toString());
        paramMap.put("listType", "10");
        paramMap.put("listSortType", "1"); // 价格升序
        paramMap.put("sortType", "0");
        paramMap.put("pageIndex", "1");
        paramMap.put("pageSize", "10");
        String jsonBody = JSONUtil.toJsonStr(paramMap);

        int attempt = 0;

        // 🔥 开启重试循环
        while (attempt < MAX_RETRIES) {
            attempt++;

            // 3. 获取随机代理
            Proxy proxy = null;
            // 如果是最后一次尝试，强制使用直连 (proxy = null)
            boolean isLastAttempt = (attempt == MAX_RETRIES);

            if (!isLastAttempt) {
                proxy = proxyProviderUtil.getRandomProxy();
            } else {
                log.warn("🔥 [Buff] 代理全挂，尝试【本机直连】兜底...");
            }
            String proxyStr = (proxy != null) ? proxy.address().toString() : "直连";

            try {
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
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36") // 随机 UA
                        .header("Origin", "https://youpin898.com")
                        .header("Referer", "https://youpin898.com/")
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json, text/plain, */*")
                        .header("secret-v", "h5_v1")
                        .timeout(6000); // 代理通常较慢，超时设为 6s

                // 🔥 注入代理
                if (proxy != null) {
                    request.setProxy(proxy);
                }

                try (HttpResponse response = request.execute()) {
                    String res = response.body();

                    // 4. 【WAF 防御】拦截 HTML 响应
                    // 很多时候免费代理会被目标网站识别并返回验证码页面
                    if (StrUtil.isBlank(res) || !StrUtil.startWith(res.trim(), "{")) {
                        String preview = StrUtil.sub(res, 0, 100).replace("\n", "");
                        log.warn("⚠️ [悠悠有品] 第{}次被拦截/返回HTML: {}... (Proxy: {})", attempt, preview, proxyStr);

                        // 🚨 踢出坏代理
                        if (proxy != null) proxyProviderUtil.removeBadProxy(proxy);
                        continue;
                    }

                    // 5. 解析 JSON
                    JSONObject json = JSONUtil.parseObj(res);
                    Integer code = json.getInt("Code");
                    if (code == null) code = json.getInt("code");

                    if (code != null && code == 0) {
                        // 成功拿到数据
                        Object dataObj = json.get("Data");
                        if (dataObj == null) dataObj = json.get("data");

                        JSONArray items = null;
                        if (dataObj instanceof JSONArray) {
                            items = (JSONArray) dataObj;
                        } else if (dataObj instanceof JSONObject) {
                            // 兼容 data 为对象的情况 (CommodityList)
                            JSONObject obj = (JSONObject) dataObj;
                            if (obj.containsKey("CommodityList")) {
                                items = obj.getJSONArray("CommodityList");
                            }
                        }

                        if (items != null && !items.isEmpty()) {
                            JSONObject cheapestItem = items.getJSONObject(0);
                            BigDecimal price = cheapestItem.getBigDecimal("price");
                            Integer totalCount = json.getInt("TotalCount");
                            if (totalCount == null) totalCount = json.getInt("totalCount");
                            if (totalCount == null) totalCount = items.size();

                            long cost = System.currentTimeMillis() - startTime;
                            log.info("✅ [悠悠有品] 抓取成功 (第{}次) ID:{} -> ¥{} (耗时: {}ms)", attempt, templateId, price, cost);

                            return PriceFetchResultDTO.builder()
                                    .success(true)
                                    .platform(getPlatformName())
                                    .price(price)
                                    .volume(totalCount)
                                    .targetId(templateId.toString())
                                    .build();
                        } else {
                            // 没数据，不需要重试，直接返回
                            log.info("ℹ️ [悠悠有品] ID:{} 暂无在售", templateId);
                            return PriceFetchResultDTO.fail(getPlatformName(), "暂无在售");
                        }
                    } else {
                        // 6. 处理业务错误
                        String msg = json.getStr("msg");
                        if (msg == null) msg = json.getStr("Msg");

                        // ⚠️ 特殊处理：如果提示“操作频繁”，说明当前 IP 或 Token 受限
                        // 此时应该换个 IP 重试，而不是直接报错
                        if (StrUtil.contains(msg, "频繁")) {
                            log.warn("⚠️ [悠悠有品] 触发频率限制 (Proxy: {})，尝试更换代理...", proxyStr);
                            if (proxy != null) proxyProviderUtil.removeBadProxy(proxy);
                            continue;
                        }

                        log.warn("❌ [悠悠有品] 业务报错: {} (Proxy: {})", msg, proxyStr);
                        return PriceFetchResultDTO.fail(getPlatformName(), "API拒绝:" + msg);
                    }
                }
            } catch (Exception e) {
                // 7. 处理网络超时
                log.warn("⚠️ [悠悠有品] 第{}次连接超时: {} (Proxy: {})", attempt, e.getMessage(), proxyStr);
                if (proxy != null) proxyProviderUtil.removeBadProxy(proxy);
            } finally {
                long sleep = RandomUtil.randomLong(500, 1500);
                ThreadUtil.sleep(sleep);
            }
        }

        log.error("❌ [悠悠有品] ID:{} 重试 {} 次全部失败", templateId, MAX_RETRIES);
        return PriceFetchResultDTO.fail(getPlatformName(), "重试耗尽/无可用代理");
    }

    /**
     * 🔥 核心：并发模拟批量
     * 同时发起 N 个 HTTP 请求，等待全部完成后聚合结果
     */
    @Override
    public List<PriceFetchResultDTO> batchFetchPrices(List<String> ids) {
        // 线程安全的 List 用于收集结果
        List<PriceFetchResultDTO> results = Collections.synchronizedList(new ArrayList<>());
        if (CollectionUtil.isEmpty(ids)) {
            return results;
        }

        long start = System.currentTimeMillis();

        // 1. 创建并发任务
        List<CompletableFuture<Void>> futures = ids.stream()
                .map(id -> CompletableFuture.runAsync(() -> {
                    PriceFetchResultDTO dto = fetchPrice(Long.valueOf(id));
                    if (dto != null) {
                        results.add(dto);
                    }
                }, youpinExecutor))
                .toList();

        // 2. 等待所有任务完成 (join 会阻塞直到所有子线程结束)
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            log.error("⚠️ [悠悠] 并发任务执行异常", e);
        }

        // 如果传入了 ID，但结果是空的，很有可能是所有请求都超时了
        if (CollectionUtil.isNotEmpty(ids) && CollectionUtil.isEmpty(results)) {
            throw new BusinessException("悠悠 批量并发全部失败，触发补偿机制");
        }

        log.info("📦 [悠悠并发] 请求 {} 个ID，成功 {} 个，耗时 {}ms", ids.size(), results.size(), System.currentTimeMillis() - start);
        return results;
    }
}
