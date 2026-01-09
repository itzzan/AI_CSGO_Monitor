package com.zan.csgo.task;

import cn.hutool.core.collection.CollectionUtil;
import com.zan.csgo.constant.RedisKeyConstant;
import com.zan.csgo.enums.SkinPriorityEnum;
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

    /**
     * 初始化每天的默认获取饰品价格任务
     */
    //@Scheduled(fixedDelay = 1000 * 60 * 60 * 24) // 每1天派发一轮
    public void dispatchTasks() {
        log.info("📢 [调度中心] 开始派发任务...");

        // 1. 获取所有 ID
        List<Long> ids = skinItemService.selectAllIdList();

        if (ids.isEmpty()) return;

        // 2. 推送到 Redis List (RPUSH)
        // 转换成 String 数组批量推送，减少网络开销
        String[] idStrs = ids.stream().map(String::valueOf).toArray(String[]::new);
        stringRedisTemplate.opsForList().rightPushAll(RedisKeyConstant.QUEUE_KEY, idStrs);

        log.info("📢 [调度中心] 派发完成，新增任务数: {}", ids.size());
    }

    /**
     * 🧊【普通赛道】每 4 小时派发一次
     * 逻辑：查询 priority = 0 的饰品，推送到冷门队列
     */
    @Scheduled(fixedDelay = 1000 * 60 * 60 * 4)
    public void dispatchColdTasks() {
        Long size = stringRedisTemplate.opsForList().size(RedisKeyConstant.QUEUE_COMMON);
        if (size != null && size > 1000) {
            log.warn("🧊 [调度] 普通队列堆积 (剩余{}个)，跳过本次派发", size);
            return;
        }

        log.info("🧊 [调度] 开始加载普通任务...");
        pushTasksToQueue(SkinPriorityEnum.COMMON.getCode(), RedisKeyConstant.QUEUE_COMMON, SkinPriorityEnum.COMMON.getDesc());
    }

    /**
     * 🔥【热门赛道】每 5 分钟派发一次
     * 逻辑：查询 priority = 1 的饰品，推送到热门队列
     */
    @Scheduled(fixedDelay = 1000 * 60 * 5)
    public void dispatchHotTasks() {
        // 防止队列堆积过深（如果上次还没跑完，这次先别推了，避免 Redis 炸了）
        Long size = stringRedisTemplate.opsForList().size(RedisKeyConstant.QUEUE_HOT);
        if (size != null && size > 50) {
            log.warn("🔥 [调度] 热门队列堆积 (剩余{}个)，跳过本次派发", size);
            return;
        }

        log.info("🔥 [调度] 开始加载热门任务...");
        pushTasksToQueue(SkinPriorityEnum.HOT.getCode(), RedisKeyConstant.QUEUE_HOT, SkinPriorityEnum.HOT.getDesc());
    }

    /**
     * ❄️【冷门赛道】每 12 小时派发一次
     * 逻辑：查询 priority = 2 的饰品，推到冷门队列
     */
    @Scheduled(fixedDelay = 1000 * 60 * 60 * 12)
    public void dispatchPriorityTasks() {
        Long size = stringRedisTemplate.opsForList().size(RedisKeyConstant.QUEUE_COLD);
        if (size != null && size > 100) {
            log.warn("🔥 [调度] 冷门队列堆积 (剩余{}个)，跳过本次派发", size);
            return;
        }

        log.info("🔥 [调度] 开始加载冷门任务...");
        pushTasksToQueue(SkinPriorityEnum.ICE.getCode(), RedisKeyConstant.QUEUE_COLD, SkinPriorityEnum.ICE.getDesc());
    }

    /**
     * 通用推数逻辑
     */
    private void pushTasksToQueue(Integer priority, String queueKey, String logPrefix) {
        // 1. 只查 ID，减少数据库压力
        List<Long> idList = skinItemService.selectAllIdListByPriority(priority);

        if (CollectionUtil.isEmpty(idList)) {
            log.warn("📢 [调度] {}任务为空，跳过", logPrefix);
            return;
        }

        // 转换成String类型
        List<String> idStrList = idList.stream().map(String::valueOf).toList();

        // 2. 批量推入 Redis (RPUSH)
        // 建议分批推，防止一次网络包过大，这里假设 ID 不会超过几万，直接推没问题
        stringRedisTemplate.opsForList().rightPushAll(queueKey, idStrList);

        log.info("📢 [调度] {}任务派发完成，新增 {} 个", logPrefix, idStrList.size());
    }
}