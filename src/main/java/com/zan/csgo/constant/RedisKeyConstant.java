package com.zan.csgo.constant;

/**
 * @Author Zan
 * @Create 2026/1/9 11:43
 * @ClassName: RedisKeyConstant
 * @Description : Redis 队列 Key 常量
 */
public class RedisKeyConstant {

    /**
     * 所有饰品队列 Key
     */
    public static final String QUEUE_KEY = "csgo:task:queue:default";

    /**
     * 普通饰品队列 Key
     */
    public static final String QUEUE_COMMON = "csgo:task:queue:common";

    /**
     * 热门饰品队列 Key
     */
    public static final String QUEUE_HOT = "csgo:task:queue:hot";

    /**
     * 冷门饰品队列 Key
     */
    public static final String QUEUE_COLD = "csgo:task:queue:cold";

    // 国内代理池 (Buff, 悠悠)
    public static final String PROXY_CN = "csgo:proxy:cn";

    // 海外代理池 (Steam)
    public static final String PROXY_GLOBAL = "csgo:proxy:global";

}
