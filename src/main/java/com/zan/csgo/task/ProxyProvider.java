package com.zan.csgo.task;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @Author Zan
 * @Create 2026/1/8 10:00
 * @ClassName: ProxyProvider
 * @Description : 免费代理池提供者
 *                对接 Redis 中的 use_proxy 键
 */
@Component
@Slf4j
public class ProxyProvider {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 这是 jhao104/proxy_pool 默认存的好代理的 Key
    private static final String REDIS_KEY = "use_proxy";

    /**
     * 随机获取一个可用代理
     */
    public Proxy getRandomProxy() {
        try {
            // 1. 从 Redis Hash 中获取所有可用代理
            // (注意：如果代理池很大，建议用 sRandMember 或 hKeys 优化，这里演示简单逻辑)
            Set<Object> keys = stringRedisTemplate.opsForHash().keys(REDIS_KEY);

            if (CollectionUtil.isEmpty(keys)) {
                log.warn("⚠️ [代理池] Redis 中没有可用代理！正在裸奔...");
                return null;
            }

            // 2. 随机取一个 (负载均衡)
            List<Object> proxyList = new ArrayList<>(keys);
            String proxyStr = (String) RandomUtil.randomEle(proxyList); // 格式如 "127.0.0.1:8080"

            if (StrUtil.isBlank(proxyStr)) {
                return null;
            }

            // 3. 解析 IP 和 Port
            String[] parts = proxyStr.split(":");
            if (parts.length != 2) {
                return null;
            }

            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            // 4. 构建 Java Proxy 对象
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ip, port));

        } catch (Exception e) {
            log.error("获取代理异常", e);
            return null;
        }
    }

    /**
     * (可选) 如果某个代理不可用，可以在 Java 端把它从 Redis 删掉
     * 防止其他线程又拿到了坏代理
     */
    public void removeBadProxy(Proxy proxy) {
        if (proxy == null || proxy.address() == null) {
            return;
        }
        try {
            InetSocketAddress address = (InetSocketAddress) proxy.address();
            String key = address.getHostString() + ":" + address.getPort();
            stringRedisTemplate.opsForHash().delete(REDIS_KEY, key);
            log.warn("🗑️ [代理池] 移除失效代理: {}", key);
        } catch (Exception e) {
            // ignore
        }
    }
}
