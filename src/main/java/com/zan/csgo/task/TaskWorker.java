package com.zan.csgo.task;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.RandomUtil;
import com.zan.csgo.service.ISkinMonitorService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @Author Zan
 * @Create 2026/1/8 10:03
 * @ClassName: TaskWorker
 * @Description : 分布式工人：从 Redis 抢任务执行
 *                支持水平扩展：你想跑快点，就在 idea 里多启动几个 Application 实例即可！
 */
@Component
@Slf4j
public class TaskWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ISkinMonitorService skinMonitorService;

    // 开关控制
    private volatile boolean running = true;

    /**
     * 项目启动后自动运行
     */
    @PostConstruct
    public void startWorker() {
        // 启动一个守护线程来消费
        new Thread(this::runConsumer, "Worker-Thread").start();
    }

    private void runConsumer() {
        log.info("👷 [工人] 已就位，等待任务...");

        while (running) {
            try {
                // 1. 阻塞式获取任务 (如果有任务就拿，没任务就等 30秒)
                // 命令: BLPOP csgo:task:queue 30
                String skinIdStr = stringRedisTemplate.opsForList().leftPop(TaskProducer.QUEUE_KEY, 30, TimeUnit.SECONDS);

                if (skinIdStr == null) {
                    continue; // 超时没取到，继续循环
                }

                Long skinId = Long.parseLong(skinIdStr);
                log.info("👷 [工人] 获取到任务 ID: {}", skinId);

                // 2. 执行核心业务 (这就是你之前的 monitorSkin)
                skinMonitorService.monitorSkin(skinId);

                // 3. 🔥 关键限流：每做一个任务，强制休息
                // 如果有代理池，可以设短一点(1-2s)；如果是单机硬跑，设长一点(5-10s)
                long sleep = RandomUtil.randomLong(2000, 5000);
                ThreadUtil.sleep(sleep);

            } catch (Exception e) {
                log.error("👷 [工人] 发生意外", e);
                // 建议：如果失败，可以将 ID 重新 rpush 回队列尾部，或者放入死信队列
                ThreadUtil.sleep(5000); // 防止死循环报错打印日志把磁盘打满
            }
        }
    }
}
