package com.zan.csgo.crawler.strategy.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zan.csgo.crawler.strategy.MarketStrategy;
import com.zan.csgo.enums.PlatformEnum;
import com.zan.csgo.model.dto.PriceFetchResultDTO;
import com.zan.csgo.utils.UserAgentUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

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

    @Override
    public String getPlatformName() {
        return PlatformEnum.BUFF.getName();
    }

    /**
     * 核心实现：根据 ID 获取价格
     * 对应你原来的 fetchBuffPrice 方法
     */
    public PriceFetchResultDTO fetchPrice(Object key) {
        String marketHashName = null;
        Long goodsId = null;

        // 1. 智能参数解析
        if (key instanceof String) {
            marketHashName = (String) key;
            // 如果传的是名字，先去搜索 ID
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
     * 内部私有方法：通过 ID 查价格
     */
    private PriceFetchResultDTO fetchPriceById(Long goodsId) {
        // 1. 基础校验
        if (ObjectUtil.isNull(goodsId) || goodsId <= 0) {
            return PriceFetchResultDTO.fail("BUFF", "Goods ID 为空，无法抓取");
        }

        log.info(">>> 开始抓取 Buff 价格 (ID: {})", goodsId);

        // 拼接 URL (注意：BUFF_PRICE_URL 末尾应该是 &goods_id=)
        String url = String.format(buffPriceApiUrl, goodsId);

        try {
            // 2. 提取 CSRF Token (这是 Buff API 成功的关键)
            String csrfToken = extractCsrfToken(buffCookie);

            // 3. 构造请求 (Header 是核心！)
            HttpRequest request = HttpRequest.get(url)
                    .header("Cookie", buffCookie)
                    // 1. 升级 User-Agent (使用最新的 Chrome 标识)
                    .header("User-Agent", UserAgentUtil.random())
                    // 2. 动态 Referer (必须指向商品详情页)
                    .header("Referer", "https://buff.163.com/goods/" + goodsId)
                    // 3. 标记 AJAX 请求 (老生常谈，但必须有)
                    .header("X-Requested-With", "XMLHttpRequest")
                    // 4. 【新增】告诉服务器我想要 JSON，不要给我 HTML
                    .header("Accept", "application/json, text/javascript, */*; q=0.01")
                    // 5. 【新增】语言权重 (中文浏览器)
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    // 6. 【新增】Sec-Fetch 系列 (模拟浏览器的跨域/同源行为，这招对网易系很有效)
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Site", "same-origin")
                    // 7. 延长超时时间
                    .timeout(8000);

            // 如果能提取到 Token，加上它
            if (StrUtil.isNotBlank(csrfToken)) {
                request.header("X-CSRFToken", csrfToken);
            }

            // 4. 发送请求
            String res = request.execute().body();

            // 5. 【安全检查】拦截 HTML 响应 (Cookie失效或触发验证码时)
            if (res != null && res.trim().startsWith("<")) {
                // 使用 Hutool 的正则工具提取 <title> 内容
                String title = cn.hutool.core.util.ReUtil.get("<title>(.*?)</title>", res, 1);

                log.error("❌ Buff 反爬拦截 (ID: {})", goodsId);
                log.error("🛑 拦截页面标题: {}", title); // <--- 关键！看这里打印了什么
                log.error("🛑 建议操作: 停止程序，更换 Cookie，增加休眠时间");

                return PriceFetchResultDTO.fail("BUFF", "反爬拦截: " + title);
            }

            // 6. 解析 JSON 数据
            JSONObject json = JSONUtil.parseObj(res);

            // 检查业务状态码
            if ("OK".equals(json.getStr("code"))) {
                JSONObject data = json.getJSONObject("data");
                JSONArray items = data.getJSONArray("items");

                // 检查是否有在售物品
                if (items != null && !items.isEmpty()) {
                    // 获取排在第一位的卖单（即全网最低价）
                    JSONObject lowestItem = items.getJSONObject(0);

                    // 提取价格 (BigDecimal)
                    BigDecimal price = lowestItem.getBigDecimal("price");

                    // 提取总在售数量 (Integer)
                    Integer totalCount = data.getInt("total_count");

                    log.info("✅ Buff抓取成功 ID:{} -> 价格: ¥{}, 在售: {}", goodsId, price, totalCount);

                    // 返回成功 DTO
                    return PriceFetchResultDTO.builder()
                            .success(true)
                            .platform("BUFF")
                            .price(price)
                            .volume(totalCount)
                            .targetId(goodsId)
                            .build();
                } else {
                    log.warn("Buff ID:{} 无在售商品", goodsId);
                    return PriceFetchResultDTO.fail("BUFF", "当前无在售商品");
                }
            } else {
                // API 返回错误码 (如 Login Required)
                String errorMsg = json.getStr("error");
                log.error("Buff API 业务错误: {}", errorMsg);
                return PriceFetchResultDTO.fail("BUFF", "API错误: " + errorMsg);
            }

        } catch (Exception e) {
            log.error("Buff 价格请求异常 (ID: " + goodsId + ")", e);
            return PriceFetchResultDTO.fail("BUFF", "系统异常: " + e.getMessage());
        }
    }

    /**
     * 搜索方法 (复用之前的逻辑)
     */
    /**
     * 搜索方法 (支持分页查找，最大查找3页)
     */
    private Long searchId(String marketHashName) {
        log.info(">>> [Buff Search] 开始搜索: {}", marketHashName);

        int page = 1;
        int maxPage = 3; // 🛑 限制最大翻页数，防止死循环封IP

        while (page <= maxPage) {
            // 1. 拼接分页参数 (&page_num=1, &page_num=2 ...)
            String url = String.format(buffSearchApiUrl, HttpUtil.encodeParams(marketHashName, null), page);

            try {
                // 2. 提取 CSRF (只需提取一次，这里简化逻辑每次都提也无所谓)
                String csrfToken = extractCsrfToken(buffCookie);

                HttpRequest request = HttpRequest.get(url)
                        .header("Cookie", buffCookie)
                        .header("User-Agent", UserAgentUtil.random()) // 记得用全套 Header
                        .header("Referer", "https://buff.163.com/market/")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Accept", "application/json, text/javascript, */*; q=0.01")
                        .timeout(8000);

                if (StrUtil.isNotBlank(csrfToken)) {
                    request.header("X-CSRFToken", csrfToken);
                }

                String res = request.execute().body();

                // 3. HTML 拦截检查
                if (res != null && StrUtil.startWith(StrUtil.trim(res), "<")) {
                    String title = ReUtil.get("<title>(.*?)</title>", res, 1);
                    log.error("❌ [Buff Search] 反爬拦截! Page: {}, 标题: {}", page, title);
                    return null; // 遇到反爬直接放弃，不要重试了
                }

                JSONObject json = JSONUtil.parseObj(res);
                if ("OK".equals(json.getStr("code"))) {
                    JSONArray items = json.getJSONObject("data").getJSONArray("items");

                    // 如果当前页是空的，说明没数据了，直接退出
                    if (items == null || items.isEmpty()) {
                        log.warn("⚠️ [Buff Search] 第 {} 页无数据，停止搜索: {}", page, marketHashName);
                        return null;
                    }

                    // 4. 遍历当前页
                    for (int i = 0; i < items.size(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        // 完全匹配检查
                        if (marketHashName.equals(item.getStr("market_hash_name"))) {
                            Long goodsId = item.getLong("id");
                            log.info("✅ 找到映射! (Page: {}) {} -> {}", page, marketHashName, goodsId);
                            return goodsId;
                        }
                    }

                    log.info("ℹ️ 第 {} 页未找到，准备翻页...", page);

                } else {
                    log.error("[Buff Search] API错误: {}", json.getStr("error"));
                    return null;
                }

            } catch (Exception e) {
                log.error("[Buff Search] 异常", e);
                return null;
            }

            // 5. 翻页前的防封休眠
            page++;
            if (page <= maxPage) {
                try {
                    // 翻页因为是连续请求，必须加长等待！建议 2秒以上
                    Thread.sleep(2000);
                } catch (InterruptedException e) {}
            }
        }

        log.warn("❌ [Buff Search] 翻阅了 {} 页仍未找到: {}", maxPage, marketHashName);
        return null;
    }

    /**
     * 辅助方法：从 Cookie 字符串中提取 csrf_token
     */
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
