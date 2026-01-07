package com.zan.csgo.service.impl;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.zan.csgo.service.INotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author Zan
 * @Create 2026/1/7 16:38
 * @ClassName: NotificationServiceImpl
 * @Description : 通知服务实现类
 */
@Service
@Slf4j
public class NotificationServiceImpl implements INotificationService {

    @Value("${csgo.notification.pushplus.token}")
    private String pushPlusToken;

    private static final String PUSH_URL = "http://www.pushplus.plus/send";

    /**
     * 发送价格异动报警 (异步执行，不阻塞主流程)
     */
    @Async
    @Override
    public void sendPriceAlert(String skinName, String platform, BigDecimal oldPrice, BigDecimal newPrice, String changeRate) {

        log.info(">>> 准备发送微信推送: {} - {}", skinName, changeRate);

        try {
            // 1. 构造消息标题
            String title = String.format("🚨 饰品异动: %s %s", skinName, changeRate);

            // 2. 构造消息内容 (支持 HTML)
            // 我们可以做的漂亮一点
            StringBuilder content = new StringBuilder();
            content.append("<h3>🔥 发现价格剧烈波动</h3>");
            content.append("<p><b>饰品名称：</b>").append(skinName).append("</p>");
            content.append("<p><b>所属平台：</b>").append(platform).append("</p>");
            content.append("<hr/>");
            content.append("<p style='color:gray'>1分钟前价格：</p>");
            content.append("<h2>¥ ").append(oldPrice).append("</h2>");
            content.append("<p style='color:red'>当前最新价：</p>");
            content.append("<h1 style='color:red'>¥ ").append(newPrice).append("</h1>");
            content.append("<p><b>涨跌幅度：</b>").append(changeRate).append("</p>");
            content.append("<hr/>");
            content.append("<p style='font-size:12px;color:gray'>CSGO AI 监控系统</p>");

            // 3. 构造请求参数
            Map<String, Object> paramMap = new HashMap<>();
            paramMap.put("token", pushPlusToken);
            paramMap.put("title", title);
            paramMap.put("content", content.toString());
            paramMap.put("template", "html"); // 使用 HTML 模板

            // 4. 发送请求
            String result = HttpUtil.post(PUSH_URL, JSONUtil.toJsonStr(paramMap));
            log.info(">>> 微信推送结果: {}", result);

        } catch (Exception e) {
            log.error("微信推送失败", e);
        }
    }
}
