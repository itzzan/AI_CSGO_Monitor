package com.zan.csgo.task;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import com.google.common.collect.Lists;
import com.zan.csgo.constant.RedisKeyConstant;
import com.zan.csgo.crawler.strategy.impl.BuffStrategy;
import com.zan.csgo.crawler.strategy.impl.YoupinStrategy;
import com.zan.csgo.enums.PlatformEnum;
import com.zan.csgo.enums.SkinPriorityEnum;
import com.zan.csgo.exception.BusinessException;
import com.zan.csgo.model.dto.PriceFetchResultDTO;
import com.zan.csgo.model.entity.SkinItemEntity;
import com.zan.csgo.model.entity.SkinPriceHistoryEntity;
import com.zan.csgo.service.ISkinItemService;
import com.zan.csgo.service.ISkinPriceHistoryService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author Zan
 * @Create 2026/1/8 10:03
 * @ClassName: TaskWorker
 * @Description : 分布式批量工人 (优先级队列 + 批量消费 + 自动映射入库)
 */
@Component
@Slf4j
public class TaskWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ISkinItemService skinItemService;

    @Resource
    private ISkinPriceHistoryService priceHistoryService;

    @Resource
    private BuffStrategy buffStrategy;

    @Resource
    private YoupinStrategy youpinStrategy;

    // 每次处理的批量大小（BUFF）
    private static final int BATCH_SIZE = 80;

    /**
     * 启动后自动运行消费者线程
     */
    @PostConstruct
    public void startWorker() {
        new Thread(this::runConsumer, "Batch-Worker-Thread-1").start();
        new Thread(this::runConsumer, "Batch-Worker-Thread-2").start();
        new Thread(this::runConsumer, "Batch-Worker-Thread-3").start();
        new Thread(this::runConsumer, "Batch-Worker-Thread-4").start();
        new Thread(this::runConsumer, "Batch-Worker-Thread-5").start();
    }

    private void runConsumer() {
        log.info("👷 [工人] 已就位，准备开始搬砖...");

        while (true) {
            try {
                List<String> idStrList;
                String sourceQueue;

                // --- 1. 优先级获取任务 (热 -> 普 -> 冷) ---
                idStrList = stringRedisTemplate.opsForList().leftPop(RedisKeyConstant.QUEUE_HOT, BATCH_SIZE);
                sourceQueue = SkinPriorityEnum.HOT.getDesc();

                if (CollectionUtil.isEmpty(idStrList)) {
                    idStrList = stringRedisTemplate.opsForList().leftPop(RedisKeyConstant.QUEUE_COMMON, BATCH_SIZE);
                    sourceQueue = SkinPriorityEnum.COMMON.getDesc();
                }

                if (CollectionUtil.isEmpty(idStrList)) {
                    idStrList = stringRedisTemplate.opsForList().leftPop(RedisKeyConstant.QUEUE_COLD, BATCH_SIZE);
                    sourceQueue = SkinPriorityEnum.ICE.getDesc();
                }

                // 如果所有队列都空了，休息一会儿
                if (CollectionUtil.isEmpty(idStrList)) {
                    ThreadUtil.sleep(5000);
                    continue;
                }

                log.info("👷 [工人] 抢到 {} 个[{}]任务", idStrList.size(), sourceQueue);

                // --- 2. 准备数据 ---
                List<Long> dbIds = idStrList.stream().map(Long::parseLong).collect(Collectors.toList());
                // 批量查询数据库实体 (我们需要用它里面的 BuffId 和 YoupinId)
                List<SkinItemEntity> items = skinItemService.listByIds(dbIds);

                if (CollectionUtil.isEmpty(items)) {
                    log.warn("⚠️ ID对应的数据库记录不存在，跳过");
                    continue;
                }

                // --- 3. 执行 监听 任务 ---
                processPlatformBatch(items);

                // --- 4. 批次间休息 ---
                long sleep = RandomUtil.randomLong(2000, 5000);
                log.info("💤 本批次结束，休息 {}ms...", sleep);
                ThreadUtil.sleep(sleep);

            } catch (Exception e) {
                log.error("❌ [工人] 发生意外", e);
                ThreadUtil.sleep(5000);
            }
        }
    }

    /**
     * 处理多平台批量请求
     */
    private void processPlatformBatch(List<SkinItemEntity> items) {
        // 1. 提取 Buff ID 列表 (过滤掉空值)
        List<String> buffIds = items.stream()
                .map(SkinItemEntity::getBuffGoodsId)
                .filter(id -> id != null && id > 0)
                .map(String::valueOf)
                .collect(Collectors.toList());

        // 2. 提取 悠悠 ID 列表
        List<String> youpinIds = items.stream()
                .map(SkinItemEntity::getYoupinId)
                .filter(id -> id != null && id > 0)
                .map(String::valueOf)
                .collect(Collectors.toList());

        youpinIds = Lists.newArrayList();

        // --- 执行 Buff 抓取 ---
        if (CollectionUtil.isNotEmpty(buffIds)) {
            try {
                // 批量抓取
                List<PriceFetchResultDTO> results = buffStrategy.batchFetchPrices(buffIds);
                saveBatchResults(items, results, PlatformEnum.BUFF);
            } catch (BusinessException e) {
                handleRollback(items, PlatformEnum.BUFF);
            } catch (Exception e) {
                log.error("❌ [Buff] 批量抓取失败", e);
            }
        }

        // --- 执行 悠悠 抓取 ---
        if (CollectionUtil.isNotEmpty(youpinIds)) {
            try {
                List<PriceFetchResultDTO> results = youpinStrategy.batchFetchPrices(youpinIds);
                saveBatchResults(items, results, PlatformEnum.YOUPIN);
            } catch (BusinessException e) {
                handleRollback(items, PlatformEnum.YOUPIN);
            } catch (Exception e) {
                log.error("❌ [悠悠] 批量抓取失败", e);
            }
        }

        // --- 执行Steam 抓取 ---
    }

    /**
     * 回滚逻辑：将失败的 DB ID 推回冷门队列
     */
    private void handleRollback(List<SkinItemEntity> items, PlatformEnum platformEnum) {
        log.warn("♻️ [补偿机制] {} 批量部分失败，准备回滚...", platformEnum.getName());
        // 筛选出该平台涉及到的 数据库ID
        List<String> rollbackIds = items.stream()
                .map(i -> String.valueOf(i.getId()))
                .collect(Collectors.toList());

        if (CollectionUtil.isNotEmpty(rollbackIds)) {
            log.warn("♻️ [补偿机制] {} 批量全部失败，{} 个ID已回滚至冷门队列", platformEnum.getName(), rollbackIds.size());
            stringRedisTemplate.opsForList().rightPushAll(RedisKeyConstant.QUEUE_COLD, rollbackIds);
        }
    }

    /**
     * 统一结果保存逻辑 (核心：ID 映射 + 批量插入)
     */
    private void saveBatchResults(List<SkinItemEntity> items, List<PriceFetchResultDTO> results, PlatformEnum platformEnum) {
        if (CollectionUtil.isEmpty(results)) {
            return;
        }

        // 1. 构建映射 Map: 平台ID -> 数据库实体
        Map<String, SkinItemEntity> map = items.stream()
                .collect(Collectors.toMap(
                        item -> String.valueOf(ObjectUtil.equal(PlatformEnum.BUFF, platformEnum) ? item.getBuffGoodsId()
                                : ObjectUtil.equal(PlatformEnum.YOUPIN, platformEnum) ? item.getYoupinId() : 0),
                        item -> item,
                        (v1, v2) -> v1 // 键冲突取第一个
                ));

        // 2. 遍历结果并做 ID 替换
        List<SkinPriceHistoryEntity> skinPriceHistoryList = new ArrayList<>();
        for (PriceFetchResultDTO dto : results) {
            SkinItemEntity entity = map.get(String.valueOf(dto.getTargetId()));

            if (entity != null) {
                SkinPriceHistoryEntity history = new SkinPriceHistoryEntity();
                history.setSkinId(entity.getId());
                history.setPlatform(platformEnum.getName());
                history.setPrice(dto.getPrice());
                history.setVolume(dto.getVolume());
                history.setCreatedAt(LocalDateTime.now());
                skinPriceHistoryList.add(history);
            }
        }

        // 3. 调用 Service 进行批量插入 (比循环单次插入快得多)
        if (CollectionUtil.isNotEmpty(skinPriceHistoryList)) {
            try {
                priceHistoryService.saveBatch(skinPriceHistoryList);
                log.info("💾 [{}] 成功入库 {} 条数据", platformEnum.getName(), skinPriceHistoryList.size());
            } catch (Exception e) {
                log.error("❌ [{}] 批量入库失败", platformEnum.getName(), e);
            }
        }
    }
}