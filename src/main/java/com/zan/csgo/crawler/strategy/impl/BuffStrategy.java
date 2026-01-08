package com.zan.csgo.crawler.strategy.impl;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zan.csgo.crawler.strategy.MarketStrategy;
import com.zan.csgo.enums.PlatformEnum;
import com.zan.csgo.model.dto.PriceFetchResultDTO;
import com.zan.csgo.task.ProxyProvider;
import com.zan.csgo.utils.UserAgentUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.Proxy;

/**
 * @Author Zan
 * @Create 2026/1/7 09:41
 * @ClassName: BuffStrategy
 * @Description : Buff 抓取类
 */
@Slf4j
@Component
public class BuffStrategy implements MarketStrategy {

    @Value("${csgo.monitor.buff.cookie}")
    private String buffCookie;

    @Value("${csgo.monitor.buff.price-api-url}")
    private String buffPriceApiUrl;

    @Value("${csgo.monitor.buff.search-api-url}")
    private String buffSearchApiUrl;

    @Resource
    private ProxyProvider proxyProvider;

    private static final int MAX_RETRIES = 5;

    @Override
    public String getPlatformName() {
        return PlatformEnum.BUFF.getName();
    }

    /**
     * 核心实现：根据 ID 获取价格
     * 对应你原来的 fetchBuffPrice 方法
     */
    @Override
    public PriceFetchResultDTO fetchPrice(Object key) {
        String marketHashName = null;
        Long goodsId = null;

        // 1. 智能参数解析
        if (key instanceof String) {
            marketHashName = (String) key;
            // 搜索 ID 也需要走代理，防止搜索阶段就被封 IP
            goodsId = searchId(marketHashName);
            if (goodsId == null) {
                return PriceFetchResultDTO.fail(getPlatformName(), "搜索不到该饰品ID: " + marketHashName);
            }
        } else if (key instanceof Long) {
            // 兼容逻辑：如果调用者通过某种方式直接传了 ID (性能优化)
            goodsId = (Long) key;
        } else {
            return PriceFetchResultDTO.fail(getPlatformName(), "参数类型错误");
        }

        // 2. 拿到 ID 后，去查价格
        return fetchPriceById(goodsId);
    }

    /**
     * 内部私有方法：通过 ID 查价格 (集成重试与代理)
     */
    private PriceFetchResultDTO fetchPriceById(Long goodsId) {
        if (ObjectUtil.isNull(goodsId) || goodsId <= 0) {
            return PriceFetchResultDTO.fail("BUFF", "Goods ID 为空");
        }

        // 拼接 URL
        String url = String.format(buffPriceApiUrl, goodsId);

        int attempt = 0;

        // 🔥 开启重试循环
        while (attempt < MAX_RETRIES) {
            attempt++;

            // 1. 获取随机代理
            Proxy proxy = null;
            // 如果是最后一次尝试，强制使用直连 (proxy = null)
            boolean isLastAttempt = (attempt == MAX_RETRIES);

            if (!isLastAttempt) {
                proxy = proxyProvider.getRandomProxy();
            } else {
                log.warn("🔥 [Buff] 代理全挂，尝试【本机直连】兜底...");
            }
            String proxyStr = (proxy != null) ? proxy.address().toString() : "直连";

            try {
                // 2. 提取 CSRF Token
                String csrfToken = extractCsrfToken(buffCookie);

                // 3. 构造请求 (保留你原有的优秀 Header)
                HttpRequest request = HttpRequest.get(url)
                        .header("Cookie", buffCookie)
                        // 1. 升级 User-Agent (使用最新的 Chrome 标识)
                        .header("User-Agent", UserAgentUtil.random()) // 随机 UA
                        // 2. 动态 Referer (必须指向商品详情页)
                        .header("Referer", "https://buff.163.com/goods/" + goodsId)
                        // 3. 标记 AJAX 请求 (老生常谈，但必须有)
                        .header("X-Requested-With", "XMLHttpRequest")
                        // 4. 告诉服务器我想要 JSON，不要给我 HTML
                        .header("Accept", "application/json, text/javascript, */*; q=0.01")
                        // 5. 语言权重 (中文浏览器)
                        .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                        // 6. 模拟同源请求，防检测
                        .header("Sec-Fetch-Dest", "empty")
                        .header("Sec-Fetch-Mode", "cors")
                        .header("Sec-Fetch-Site", "same-origin")
                        // 7. 延长超时时间
                        .timeout(8000); // 代理慢，超时给8秒

                if (StrUtil.isNotBlank(csrfToken)) {
                    request.header("X-CSRFToken", csrfToken);
                }

                // 🔥 注入代理
                if (proxy != null) {
                    request.setProxy(proxy);
                }

                // 4. 发送请求 (使用 try-with-resources 自动关闭连接)
                try (HttpResponse response = request.execute()) {
                    String res = response.body();

                    // 5. 【WAF 防御】拦截 HTML 响应
                    if (StrUtil.isBlank(res) || StrUtil.trim(res).startsWith("<")) {
                        String title = ReUtil.get("<title>(.*?)</title>", res, 1);
                        log.warn("⚠️ [Buff] 第{}次被墙/返回HTML: {} (Proxy: {})", attempt, title, proxyStr);

                        // 🚨 关键：如果是坏代理，从 Redis 移除，防止下次还用到它
                        if (proxy != null) {
                            proxyProvider.removeBadProxy(proxy);
                        }
                        continue; // 换下一个 IP 重试
                    }

                    // 6. 解析 JSON
                    JSONObject json = JSONUtil.parseObj(res);
                    String code = json.getStr("code");

                    if ("OK".equals(code)) {
                        JSONObject data = json.getJSONObject("data");
                        JSONArray items = data.getJSONArray("items");

                        if (items != null && !items.isEmpty()) {
                            JSONObject lowestItem = items.getJSONObject(0);
                            BigDecimal price = lowestItem.getBigDecimal("price");
                            Integer totalCount = data.getInt("total_count");

                            log.info("✅ Buff抓取成功 (第{}次) ID:{} -> ¥{}", attempt, goodsId, price);

                            return PriceFetchResultDTO.builder()
                                    .success(true)
                                    .platform("BUFF")
                                    .price(price)
                                    .volume(totalCount)
                                    .targetId(goodsId)
                                    .build();
                        } else {
                            // 没货了，不需要重试
                            return PriceFetchResultDTO.fail("BUFF", "当前无在售商品");
                        }
                    } else {
                        // 7. 处理业务错误
                        String errorMsg = json.getStr("error");

                        // 如果是 Login Required，说明 Cookie 死了，重试也没用，直接退出
                        if ("Login Required".equals(errorMsg)) {
                            log.error("⛔ [Buff] Cookie 已失效，请更新！");
                            return PriceFetchResultDTO.fail("BUFF", "Cookie失效");
                        }

                        log.warn("⚠️ [Buff] API错误: {} (Proxy: {})", errorMsg, proxyStr);
                    }
                }
            } catch (Exception e) {
                // 8. 处理网络超时
                log.warn("⚠️ [Buff] 第{}次连接超时: {} (Proxy: {})", attempt, e.getMessage(), proxyStr);
                if (proxy != null) proxyProvider.removeBadProxy(proxy);
            } finally {
                long sleep = RandomUtil.randomLong(500, 1500);
                ThreadUtil.sleep(sleep);
            }
        }

        log.error("❌ [Buff] ID:{} 重试 {} 次后全部失败", goodsId, MAX_RETRIES);
        return PriceFetchResultDTO.fail("BUFF", "重试耗尽/无可用代理");
    }

    /**
     * 搜索方法 (也加上代理，防止搜索时就被封 IP)
     */
    private Long searchId(String marketHashName) {
        log.info(">>> [Buff Search] 开始搜索: {}", marketHashName);
        int page = 1;
        int maxPage = 3;

        while (page <= maxPage) {
            String url = String.format(buffSearchApiUrl, HttpUtil.encodeParams(marketHashName, null), page);

            // 为了保证搜索成功率，这里也简单加个重试，或者直接拿一个代理用
            Proxy proxy = proxyProvider.getRandomProxy();

            try {
                String csrfToken = extractCsrfToken(buffCookie);
                HttpRequest request = HttpRequest.get(url)
                        .header("Cookie", buffCookie)
                        .header("User-Agent", UserAgentUtil.random())
                        .header("Referer", "https://buff.163.com/market/")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .timeout(8000);

                if (StrUtil.isNotBlank(csrfToken)) request.header("X-CSRFToken", csrfToken);
                if (proxy != null) {
                    request.setProxy(proxy); // 👈 搜索也走代理
                }

                String res = request.execute().body();

                if (res != null && StrUtil.trim(res).startsWith("<")) {
                    log.warn("⚠️ [Buff Search] 搜索被拦截，跳过当前页 (Proxy: {})", proxy);
                    if (proxy != null) proxyProvider.removeBadProxy(proxy);
                    // 搜索阶段被拦截通常直接导致失败，这里简单处理为返回 null，让外层重试
                    return null;
                }

                JSONObject json = JSONUtil.parseObj(res);
                if ("OK".equals(json.getStr("code"))) {
                    JSONArray items = json.getJSONObject("data").getJSONArray("items");
                    if (items == null || items.isEmpty()) return null;

                    for (int i = 0; i < items.size(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        if (marketHashName.equals(item.getStr("market_hash_name"))) {
                            Long goodsId = item.getLong("id");
                            log.info("✅ 找到映射! {} -> {}", marketHashName, goodsId);
                            return goodsId;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[Buff Search] 搜索异常", e);
            }
            page++;
        }
        return null;
    }

    private String extractCsrfToken(String cookie) {
        if (StrUtil.isBlank(cookie)) {
            return null;
        }
        try {
            String[] split = cookie.split(";");
            for (String s : split) {
                String trim = s.trim();
                if (trim.startsWith("csrf_token=")) {
                    return trim.substring("csrf_token=".length());
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}
