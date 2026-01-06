package com.zan.csgo.crawler;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Author Zan
 * @Create 2026/1/6 17:00
 * @ClassName: MarketCrawlerService
 * @Description : 各大市场爬虫服务
 */
@Service
@Slf4j
public class MarketCrawlerService {

    public static final String TARGET_NAME = "AK-47 | Redline (Field-Tested)";
    public static void main(String[] args) throws InterruptedException {
        System.out.println("========= 测试开始 =========");

        MarketCrawlerService marketCrawlerService = new MarketCrawlerService();

        // 1. 测试 Steam 数据抓取
        marketCrawlerService.fetchSteamData(TARGET_NAME);

        System.out.println("\n--------------------------------\n");

        // 2. 测试 Buff ID 映射 (Search)
        Integer buffId = marketCrawlerService.searchBuffId(TARGET_NAME);

        System.out.println("\n--------------------------------\n");

        // 3. 测试 Buff 价格抓取 (如果 ID 找到了)
        if (buffId != null) {
            // 稍微停顿一下，防止被 Buff 判定请求过快
            Thread.sleep(2000);
            marketCrawlerService.fetchBuffPrice(buffId);
        } else {
            System.err.println("测试中断：未获取到 Buff ID，请检查 Cookie 或网络");
        }

        System.out.println("========= 测试结束 =========");
    }

    // Steam API (无需Cookie，但频率限制严)
    private static final String STEAM_API_URL = "https://steamcommunity.com/market/priceoverview/?appid=730&currency=23&market_hash_name=";

    // Buff 搜索 API (用于查 ID)
    private static final String BUFF_SEARCH_URL = "https://buff.163.com/api/market/goods?game=csgo&page_num=1&search=";

    // Buff 价格 API (用于查价格)
    private static final String BUFF_PRICE_URL = "https://buff.163.com/api/market/goods/sell_order?game=csgo&page_num=1&sort_by=default&mode=&allow_tradable_cooldown=1&goods_id=";

    /**
     * 测试用：请填入你浏览器抓取的真实 Cookie
     */
    private static final String BUFF_COOKIE = "mcdev_cookie_id=rwuml_1766390467; timing_user_id=time_zIkYNMjzBc; Locale-Supported=zh-Hans; game=csgo; Device-Id=Yo3umOFnMmt09TDzlsrN; NTES_YD_SESS=Wb0_OHbAgntbGGYiuAtsXHKrW3aVyDo8hR9LTqXcTdUk2wCz2RvHFKpHshTiRI29IpEpfzSJr3DXLiNVYgAStAWHv_5GLy8UZLjsRdcAB7mtY4bPfJLaCXnb5nzL6CT3rOpqOoiF.ksW9YHiB4QV1sV5iLW4mnhl.aS9PZaEGbwMRi6AjwQCbLvT5lrdWvurB1ursMf6S3T1aWFeuSRmm0IoTUqCrb4jj9NXPGoeO5dDA; S_INFO=1767691020|0|0&60##|18858411495; P_INFO=18858411495|1767691020|1|netease_buff|00&99|null&null&null#zhj&330100#10#0|&0|null|18858411495; remember_me=U1079962975|y2rRJTTfAJksQU0Ot7j972jdlc2kbnPs; session=1-TlOOT9V8T_v31dncQH8DKO2c3ervbKC21wn-ZJRNPLfe2022732295; csrf_token=Ijg4NzhmODQ0OWRmNjQ0NjJmNDk5ZDU4ZTg0MTVhMDRjNDdmNDkxZWIi.aVzTXg.mA-Dx0PNRoB1TmXqFQlxCOLObjk";

    /**
     * 1. [Steam] 获取行情
     */
    public void fetchSteamData(String marketHashName) {
        log.info(">>> 开始抓取 Steam: {}", marketHashName);
        // URL 编码很重要！
        String url = STEAM_API_URL + HttpUtil.encodeParams(marketHashName, null);

        try {
            // Steam 有时需要梯子，如果本地跑不通，可以配置代理：.setHttpProxy("127.0.0.1", 7890)
            String res = HttpUtil.get(url, 5000);

            log.info("--- Steam 原始响应 ---");
            log.info(JSONUtil.formatJsonStr(res)); // 格式化打印，方便你看结构

            // TODO: 在这里调用 Service 将数据存入 skin_price_history 表

        } catch (Exception e) {
            log.error("Steam 请求失败", e);
        }
    }

    /**
     * 2. [Buff] 搜索并获取 Goods ID (映射步骤)
     * @return 找到的 goods_id，未找到返回 null
     */
    public Integer searchBuffId(String marketHashName) {
        log.info(">>> 开始在 Buff 搜索 ID: {}", marketHashName);
        String url = BUFF_SEARCH_URL + HttpUtil.encodeParams(marketHashName, null);

        try {
            // 1. 提取 CSRF Token (这是 Buff API 成功的关键)
            String csrfToken = extractCsrfToken(BUFF_COOKIE);

            // 2. 构造请求
            HttpRequest request = HttpRequest.get(url)
                    .header("Cookie", BUFF_COOKIE)
                    // 必须伪装成浏览器
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    // 【关键】Buff 强校验 Referer，没有这个直接 403 或跳转首页
                    .header("Referer", "https://buff.163.com/market/")
                    // 【关键】告诉服务器这是 AJAX 请求，尽量返回 JSON 而不是 HTML 重定向
                    .header("X-Requested-With", "XMLHttpRequest")
                    .timeout(5000);

            // 如果能提取到 Token，加上它
            if (StrUtil.isNotBlank(csrfToken)) {
                request.header("X-CSRFToken", csrfToken);
            }

            String res = request.execute().body();

            // 3. 【关键排错】在解析前，先判断是不是 JSON
            // 如果返回的是 HTML (以 < 开头)，说明被拦截了
            if (StrUtil.isNotBlank(res) && StrUtil.startWith(StrUtil.trim(res), "<")) {
                log.error("❌ Buff 返回了 HTML 页面而不是 JSON。可能是 Cookie 失效、IP 被封或触发了验证码。");
                // 打印前 200 个字符看看是什么页面（通常是 Login 或 Captcha）
                log.error("页面内容预览: {}", StrUtil.sub(res, 0, 200));
                return null;
            }

            // 4. 安全解析 JSON
            JSONObject json = JSONUtil.parseObj(res);

            // ... 后续逻辑保持不变 ...
            if ("OK".equals(json.getStr("code"))) {
                JSONArray items = json.getJSONObject("data").getJSONArray("items");
                if (items != null && !items.isEmpty()) {
                    for (int i = 0; i < items.size(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        if (marketHashName.equals(item.getStr("market_hash_name"))) {
                            Integer goodsId = item.getInt("id");
                            log.info("✅ 找到映射! {} -> goods_id: {}", marketHashName, goodsId);
                            return goodsId;
                        }
                    }
                }
            } else {
                // 如果 code 不是 OK，打印错误信息（如 Login Required）
                log.error("Buff API 业务错误: code={}, error={}", json.getStr("code"), json.getStr("error"));
            }

        } catch (Exception e) {
            log.error("Buff 搜索请求异常", e);
        }
        return null;
    }

    /**
     * 辅助方法：从 Cookie 字符串中提取 csrf_token
     */
    private String extractCsrfToken(String cookie) {
        if (StrUtil.isBlank(cookie)) return null;
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

    /**
     * 3. [Buff] 根据 ID 获取实时价格
     */
    public void fetchBuffPrice(Integer goodsId) {
        if (goodsId == null) return;

        log.info(">>> 开始抓取 Buff 价格 (ID: {})", goodsId);

        // URL 是对的，直接拼接即可
        String url = BUFF_PRICE_URL + goodsId;

        try {
            // 1. 提取 CSRF Token
            String csrfToken = extractCsrfToken(BUFF_COOKIE);

            // 2. 构造请求 (Header 是成功的关键！)
            HttpRequest request = HttpRequest.get(url)
                    .header("Cookie", BUFF_COOKIE)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    // 【关键】必须带 Referer，且必须指向 goods 页面
                    .header("Referer", "https://buff.163.com/goods/" + goodsId)
                    // 【关键】标记为 AJAX 请求
                    .header("X-Requested-With", "XMLHttpRequest")
                    .timeout(5000);

            if (StrUtil.isNotBlank(csrfToken)) {
                request.header("X-CSRFToken", csrfToken);
            }

            String res = request.execute().body();

            // 3. 安全检查：拦截 HTML 报错
            if (res != null && res.trim().startsWith("<")) {
                log.error("❌ Buff 价格接口返回了 HTML，可能是反爬拦截。");
                log.error("预览: {}", StrUtil.sub(res, 0, 100));
                return;
            }

            // 4. 解析数据
            JSONObject json = JSONUtil.parseObj(res);
            if ("OK".equals(json.getStr("code"))) {
                // 打印前 800 个字符用于调试
                log.info("--- Buff 价格响应 ---");
                log.info(StrUtil.sub(JSONUtil.formatJsonStr(res), 0, 800) + "......");

                // 这里可以补充入库逻辑...
            } else {
                log.error("Buff 价格 API 错误: {}", json.getStr("error"));
            }

        } catch (Exception e) {
            log.error("Buff 价格请求异常", e);
        }
    }
}
