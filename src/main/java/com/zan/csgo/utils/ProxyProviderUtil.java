package com.zan.csgo.utils;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.zan.csgo.constant.RedisKeyConstant;
import com.zan.csgo.enums.PlatformEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Set;

/**
 * @Author Zan
 * @Create 2026/1/8 10:00
 * @ClassName: ProxyProvider
 * @Description : 代理池提供者
 *                对接 Redis 中的 use_proxy 键
 */
@Component
@Slf4j
public class ProxyProviderUtil {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 根据平台类型获取对应区域的代理
     */
    public Proxy getRandomProxy(PlatformEnum platform) {
        try {
            // 1. 决定使用哪个代理池
            String redisKey;
            if (PlatformEnum.STEAM.equals(platform)) {
                redisKey = RedisKeyConstant.PROXY_GLOBAL; // Steam -> 海外池
            } else if (PlatformEnum.BUFF.equals(platform) || PlatformEnum.YOUPIN.equals(platform)) {
                redisKey = RedisKeyConstant.PROXY_CN;     // Buff/悠悠 -> 国内池
            } else {
                redisKey = RedisKeyConstant.PROXY_CN;     // 默认，C5GAME/IGXE -> 国内池
            }

            // 2. 从 Redis 获取所有代理
            Set<Object> keys = stringRedisTemplate.opsForHash().keys(redisKey);
            if (keys.isEmpty()) {
                // 如果海外池没货，且是本地开发环境，可以返回 null 让它尝试直连 (走本地梯子)
                log.warn("⚠️ [{}] 代理池为空", platform.getName());
                return null;
            }

            // 3. 随机取一个
            int index = RandomUtil.randomInt(keys.size());
            String proxyStr = (String) keys.toArray()[index];

            if (StrUtil.isBlank(proxyStr)) {
                return null;
            }

            String[] parts = proxyStr.split(":");
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(parts[0], Integer.parseInt(parts[1])));

        } catch (Exception e) {
            log.error("获取代理异常", e);
            return null;
        }
    }

    /**
     * 移除失效代理 (需要判断是哪个池子的)
     */
    public void removeBadProxy(Proxy proxy, PlatformEnum platform) {
        if (proxy == null) return;
        try {
            String address = proxy.address().toString();
            if (address.startsWith("/")) address = address.substring(1);

            String redisKey = PlatformEnum.STEAM.equals(platform) ?
                    RedisKeyConstant.PROXY_GLOBAL : RedisKeyConstant.PROXY_CN;

            stringRedisTemplate.opsForHash().delete(redisKey, address);
            log.warn("🗑️ [代理池] 移除 {} 失效代理: {}", platform.getName(), address);
        } catch (Exception e) {
            // ignore
        }
    }
}
