package com.zan.csgo.crawler.strategy.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.*;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zan.csgo.crawler.strategy.MarketStrategy;
import com.zan.csgo.enums.PlatformEnum;
import com.zan.csgo.exception.BusinessException;
import com.zan.csgo.model.dto.PriceFetchResultDTO;
import com.zan.csgo.utils.ProxyProviderUtil;
import com.zan.csgo.utils.UserAgentUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

    // 线程池配置：Steam 风控极严，并发建议控制在 3~5 以内，太快会被 24小时封禁 IP
    private final ExecutorService steamExecutor = Executors.newFixedThreadPool(5);

    @Override
    public String getPlatformName() {
        return PlatformEnum.STEAM.getName();
    }

    /**
     * 单点抓取 (保留接口兼容性)
     */
    @Override
    public PriceFetchResultDTO fetchPrice(Object key) {
        return fetchSinglePrice((String) key);
    }

    /**
     * 🔥 核心：Steam 并发批量抓取
     *
     * @param hashNames 这里的 IDs 实际上是 MarketHashName 列表 (如 "AK-47 | Redline (Field-Tested)")
     */
    @Override
    public List<PriceFetchResultDTO> batchFetchPrices(List<String> hashNames) {
        // 创建线程安全的列表，用于收集并发任务的结果
        List<PriceFetchResultDTO> results = Collections.synchronizedList(new ArrayList<>());

        if (CollectionUtil.isEmpty(hashNames)) {
            return results;
        }

        long start = System.currentTimeMillis();
        log.info(">>> [Steam] 开始批量抓取 {} 个饰品...", hashNames.size());

        // 1. 提交并发任务
        List<CompletableFuture<Void>> futures = hashNames.stream()
                .map(name -> CompletableFuture.runAsync(() -> {
                    // --- 关键防封点：任务启动前随机休眠 ---
                    // 避免 5 个线程在 1ms 内同时击中 Steam 服务器
                    ThreadUtil.sleep(RandomUtil.randomInt(200, 1500));

                    PriceFetchResultDTO dto = fetchSinglePrice(name);
                    if (dto != null) {
                        results.add(dto);
                    }
                }, steamExecutor))
                .toList();

        // 2. 阻塞等待所有任务完成
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            log.error("⚠️ [Steam] 并发任务执行异常", e);
        }

        // 3. 结果校验与异常抛出
        // 如果传入了名字，但结果是空的，说明这批请求全军覆没（可能是 IP 被封了）
        // 抛出异常触发 TaskWorker 的回滚机制
        if (CollectionUtil.isNotEmpty(hashNames) && CollectionUtil.isEmpty(results)) {
            throw new BusinessException("Steam 批量并发全部失败 (可能是IP被封或网络超时)，触发回滚");
        }

        log.info("📦 [Steam并发] 请求 {} 个，成功 {} 个，耗时 {}ms", hashNames.size(), results.size(), System.currentTimeMillis() - start);

        return results;
    }

    /**
     * 单个饰品抓取逻辑
     */
    private PriceFetchResultDTO fetchSinglePrice(String marketHashName) {
        // URL 编码：Steam 名称中包含空格、括号等，必须编码 (例如 " | " -> "%20%7C%20")
        String encodedName = URLUtil.encodeAll(marketHashName);
        String url = String.format(steamPriceApiUrl, encodedName);

        int attempt = 0;
        // 单个 ID 最多重试 3 次
        while (attempt < 3) {
            attempt++;

            // 获取代理 (如果没有配置代理池，则返回 null，走直连)
            Proxy proxy = (proxyProviderUtil != null) ? proxyProviderUtil.getRandomProxy(PlatformEnum.STEAM) : null;
            String proxyStr = (proxy != null) ? proxy.address().toString() : "直连";

            try {
                HttpRequest request = HttpRequest.get(url)
                        // 伪装成真实浏览器
                        .header("User-Agent", UserAgentUtil.random())
                        // 强制中文语言环境，确保 currency=23 返回的是 "¥" 符号，方便解析
                        .header("Accept-Language", "zh-CN,zh;q=0.9")
                        .header("Referer", "https://steamcommunity.com/market/")
                        .header("Connection", "keep-alive")
                        .timeout(8000); // Steam 响应较慢，超时设长一点

                if (proxy != null) {
                    request.setProxy(proxy);
                }

                try (HttpResponse response = request.execute()) {
                    int status = response.getStatus();
                    String res = response.body();

                    // --- 状态码处理 ---
                    if (status == 429) {
                        log.warn("⚠️ [Steam] 触发429限流 (Proxy: {}) - 该IP可能已暂时被封", proxyStr);
                        if (proxy != null && proxyProviderUtil != null)
                            proxyProviderUtil.removeBadProxy(proxy, PlatformEnum.STEAM);
                        continue; // 换个代理重试
                    }

                    if (status != 200) {
                        log.warn("⚠️ [Steam] HTTP状态码 {} (Proxy: {})", status, proxyStr);
                        continue;
                    }

                    // --- 响应体验证 ---
                    if (StrUtil.isBlank(res) || !StrUtil.startWith(res.trim(), "{")) {
                        // 如果返回 HTML (通常是 WAF 页面)，视为失败
                        if (proxy != null && proxyProviderUtil != null)
                            proxyProviderUtil.removeBadProxy(proxy, PlatformEnum.STEAM);
                        continue;
                    }

                    // --- JSON 解析 ---
                    JSONObject json = JSONUtil.parseObj(res);
                    // 成功标志: "success": true
                    if (json.getBool("success") != null && json.getBool("success")) {
                        // 关键字段: lowest_price (最低价), volume (销量，可能为空)
                        // 示例: "lowest_price": "¥ 138.50"
                        String priceStr = json.getStr("lowest_price");
                        String volumeStr = json.getStr("volume");

                        BigDecimal price = parseSteamPrice(priceStr);
                        Integer volume = parseSteamVolume(volumeStr);

                        if (price != null) {
                            // 构造返回结果
                            return PriceFetchResultDTO.builder()
                                    .success(true)
                                    .platform(PlatformEnum.STEAM.getName())
                                    .targetId(marketHashName) // Steam 特殊性：用名字做 Key
                                    .price(price)
                                    .volume(volume)
                                    .build();
                        }
                    }
                }
            } catch (Exception e) {
                // 网络超时等异常，移除坏代理
                log.warn("⚠️ [Steam] 连接异常: {}", e.getMessage());
                if (proxy != null && proxyProviderUtil != null) {
                    proxyProviderUtil.removeBadProxy(proxy, PlatformEnum.STEAM);
                }
            } finally {
                // --- 关键防封点：请求结束后强制冷冻 ---
                // Steam 对连续请求非常敏感，即使换了 ID 也要休息
                ThreadUtil.sleep(RandomUtil.randomInt(1000, 2500));
            }
        }
        throw new BusinessException("Steam 3次代理重试全部失败，触发补偿机制");
    }

    /**
     * 解析 Steam 价格字符串 (核心清洗逻辑)
     * 输入示例: "¥ 1,234.50" 或 "RM 123.00"
     */
    private BigDecimal parseSteamPrice(String priceStr) {
        if (StrUtil.isBlank(priceStr)) {
            return null;
        }
        try {
            // 1. 去除所有非数字、非小数点、非逗号的字符 (去掉货币符号)
            String clean = priceStr.replaceAll("[^0-9.,]", "").trim();

            // 2. 处理千分位和小数点
            // 情况 A: "1,234.50" (标准) -> 去掉逗号 -> "1234.50"
            // 情况 B: "1234,50" (欧式) -> 逗号变点 -> "1234.50"

            if (clean.contains(",") && clean.contains(".")) {
                // 假设最后出现的是小数点 (Steam CNY 格式通常是 1,234.50)
                int commaIndex = clean.lastIndexOf(",");
                int dotIndex = clean.lastIndexOf(".");

                if (commaIndex < dotIndex) {
                    // 逗号在前，是千分位，去掉
                    clean = clean.replace(",", "");
                } else {
                    // 点在前，说明点是千分位 (欧洲格式)，去掉点，逗号变点
                    clean = clean.replace(".", "").replace(",", ".");
                }
            } else if (clean.contains(",")) {
                // 只有逗号，可能是 "1,000" (整数千分位) 或 "12,50" (小数)
                // 这里因为我们指定了 currency=23 (CNY)，通常逗号是千分位
                // 但为了保险，如果逗号后只有2位，可能是小数，否则去掉
                // 简单策略：直接去掉逗号 (CNY 返回通常是 ¥ 1,234)
                clean = clean.replace(",", "");
            }

            return NumberUtil.toBigDecimal(clean);
        } catch (Exception e) {
            log.error("❌ [Steam] 价格解析失败: raw='{}'", priceStr);
            return null;
        }
    }

    /**
     * 解析销量字符串
     * 输入示例: "1,234" 或 "123"
     */
    private Integer parseSteamVolume(String volStr) {
        if (StrUtil.isBlank(volStr)) return 0;
        try {
            // 只保留数字
            return Integer.parseInt(volStr.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
