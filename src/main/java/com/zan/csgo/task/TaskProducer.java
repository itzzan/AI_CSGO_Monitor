package com.zan.csgo.task;

import com.zan.csgo.service.ISkinItemService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author Zan
 * @Create 2026/1/8 10:03
 * @ClassName: TaskProducer
 * @Description : 调度中心：只负责生产任务，不负责执行
 *                优势：极快，1秒钟能分发 10万个任务，完全不会阻塞
 */
//@Component
@Slf4j
public class TaskProducer {

    @Resource
    private ISkinItemService skinItemService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // Redis 队列 Key
    public static final String QUEUE_KEY = "csgo:task:queue";

    @Scheduled(fixedDelay = 600000) // 每60分钟派发一轮
    public void dispatchTasks() {
        log.info("📢 [调度中心] 开始派发任务...");

        // 1. 获取所有 ID
        List<Long> ids = skinItemService.selectAllIdList();

        if (ids.isEmpty()) return;

        // 2. 推送到 Redis List (RPUSH)
        // 转换成 String 数组批量推送，减少网络开销
        String[] idStrs = ids.stream().map(String::valueOf).toArray(String[]::new);
        stringRedisTemplate.opsForList().rightPushAll(QUEUE_KEY, idStrs);

        log.info("📢 [调度中心] 派发完成，新增任务数: {}", ids.size());
    }
}