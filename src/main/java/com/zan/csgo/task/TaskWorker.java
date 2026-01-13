package com.zan.csgo.task;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import com.zan.csgo.constant.RedisKeyConstant;
import com.zan.csgo.crawler.strategy.impl.BuffStrategy;
import com.zan.csgo.crawler.strategy.impl.SteamStrategy;
import com.zan.csgo.crawler.strategy.impl.YoupinStrategy;
import com.zan.csgo.enums.PlatformEnum;
import com.zan.csgo.enums.SkinPriorityEnum;
import com.zan.csgo.exception.BusinessException;
import com.zan.csgo.mapper.SkinPriceHistoryMapper;
import com.zan.csgo.model.dto.PriceFetchResultDTO;
import com.zan.csgo.model.entity.SkinItemEntity;
import com.zan.csgo.model.entity.SkinPriceHistoryEntity;
import com.zan.csgo.service.INotificationService;
import com.zan.csgo.service.ISkinItemService;
import com.zan.csgo.service.ISkinPriceHistoryService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @Author Zan
 * @Create 2026/1/8 10:03
 * @ClassName: TaskWorker
 * @Description : 分布式批量工人 (优先级队列 + 批量消费 + 自动映射入库)
 */
//@Component
@Slf4j
public class TaskWorker {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ISkinItemService skinItemService;

    @Resource
    private ISkinPriceHistoryService priceHistoryService;

    @Resource
    private SkinPriceHistoryMapper skinPriceHistoryMapper;

    @Resource
    private BuffStrategy buffStrategy;

    @Resource
    private YoupinStrategy youpinStrategy;

    @Resource
    private SteamStrategy steamStrategy;

    @Resource
    private INotificationService notificationService;

    // 每次处理的批量大小（BUFF）
    private static final int BATCH_SIZE = 80;

    @Value("${csgo.notification.min-price:50}")
    private BigDecimal minNotifyPrice; // 最低报警金额 (默认50)

    @Value("${csgo.notification.fluctuation-limit:0.05}")
    private BigDecimal fluctuationLimit; // 波动阈值 (默认5%)

    /**
     * 启动后自动运行消费者线程
     */
    @PostConstruct
    public void startWorker() {
        for (int i = 1; i <= 5; i++) {
            new Thread(this::runConsumer, "Batch-Worker-" + i).start();
        }
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


        // 3. 提取 Steam Name 列表
        List<String> steamMarketHashNameList = items.stream()
                .map(SkinItemEntity::getSkinMarketHashName)
                .filter(StrUtil::isNotBlank)
                .toList();

        // --- 执行 Buff 抓取 ---
        if (CollectionUtil.isNotEmpty(buffIds)) {
            try {
                // 批量抓取
                List<PriceFetchResultDTO> results = buffStrategy.batchFetchPrices(buffIds);
                processResultsAndSave(items, results, PlatformEnum.BUFF);
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
                processResultsAndSave(items, results, PlatformEnum.YOUPIN);
            } catch (BusinessException e) {
                handleRollback(items, PlatformEnum.YOUPIN);
            } catch (Exception e) {
                log.error("❌ [悠悠] 批量抓取失败", e);
            }
        }

        // --- 执行Steam 抓取 ---
        if (CollectionUtil.isNotEmpty(steamMarketHashNameList)) {
            try {
                List<PriceFetchResultDTO> results = steamStrategy.batchFetchPrices(steamMarketHashNameList);
                processResultsAndSave(items, results, PlatformEnum.STEAM);
            } catch (BusinessException e) {
                handleRollback(items, PlatformEnum.STEAM);
            } catch (Exception e) {
                log.error("❌ [Steam] 批量抓取失败", e);
            }
        }
    }

    /**
     * 纯计算与通知逻辑
     */
    private void checkAndNotify(SkinItemEntity item, String platform, BigDecimal oldPrice, BigDecimal currentPrice) {
        // 防止除以0
        if (oldPrice.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        BigDecimal diff = currentPrice.subtract(oldPrice);
        BigDecimal rate = diff.divide(oldPrice, 4, RoundingMode.HALF_UP);
        BigDecimal percent = rate.multiply(new BigDecimal(100));

        // 比较波动绝对值是否超过阈值 (例如 5%)
        if (rate.abs().compareTo(fluctuationLimit) >= 0) {
            String rateStr = (percent.doubleValue() > 0 ? "+" : "") + percent.setScale(2, RoundingMode.HALF_UP) + "%";

            log.warn("🚨 [价格预警] {} ({}) : {} -> {}", item.getSkinName(), platform, oldPrice, currentPrice);

            // 发送通知
            notificationService.sendPriceAlert(
                    item.getSkinName(),
                    platform,
                    oldPrice,
                    currentPrice,
                    rateStr
            );
        }
    }

    /**
     * 回滚逻辑：将失败的 DB ID 推回冷门队列
     */
    private void handleRollback(List<SkinItemEntity> items, PlatformEnum platformEnum) {
        log.warn("♻️ [补偿机制] {} 批量部分失败，准备回滚...", platformEnum.getName());
        // 筛选出该平台涉及到的 数据库ID
        List<String> rollbackIds = items.stream()
                .filter(item -> {
                    if (PlatformEnum.BUFF.equals(platformEnum)) {
                        return item.getBuffGoodsId() != null && item.getBuffGoodsId() > 0;
                    } else if (PlatformEnum.YOUPIN.equals(platformEnum)) {
                        return item.getYoupinId() != null && item.getYoupinId() > 0;
                    } else if (PlatformEnum.STEAM.equals(platformEnum)) {
                        return StrUtil.isNotBlank(item.getSkinMarketHashName());
                    }
                    return false;
                })
                .map(i -> String.valueOf(i.getId()))
                .collect(Collectors.toList());

        if (CollectionUtil.isNotEmpty(rollbackIds)) {
            log.warn("♻️ [补偿机制] {} 批量全部失败，{} 个ID已回滚至冷门队列", platformEnum.getName(), rollbackIds.size());
            stringRedisTemplate.opsForList().rightPushAll(RedisKeyConstant.QUEUE_COLD, rollbackIds);
        }
    }

    /**
     * 核心流程：ID映射 -> 查旧价 -> 报警 -> 批量入库
     */
    private void processResultsAndSave(List<SkinItemEntity> items, List<PriceFetchResultDTO> results, PlatformEnum platformEnum) {
        if (CollectionUtil.isEmpty(results)) {
            return;
        }

        // 1. 构建映射 Map: 平台Key -> 数据库实体
        // Buff/Youpin 用 ID 匹配，Steam 用 HashName 匹配
        Map<String, SkinItemEntity> map = items.stream()
                .collect(Collectors.toMap(
                        item -> {
                            if (PlatformEnum.BUFF.equals(platformEnum)) return String.valueOf(item.getBuffGoodsId());
                            if (PlatformEnum.YOUPIN.equals(platformEnum)) return String.valueOf(item.getYoupinId());
                            if (PlatformEnum.STEAM.equals(platformEnum)) return item.getSkinMarketHashName(); // Steam Key
                            return "";
                        },
                        item -> item,
                        (v1, v2) -> v1
                ));

        // 2. 批量查旧价 (优化性能)
        List<Long> successDbIds = new ArrayList<>();
        for (PriceFetchResultDTO dto : results) {
            SkinItemEntity entity = map.get(String.valueOf(dto.getTargetId()));
            if (entity != null) successDbIds.add(entity.getId());
        }

        Map<Long, BigDecimal> oldPriceMap = new HashMap<>();
        if (CollectionUtil.isNotEmpty(successDbIds)) {
            // 需要在 Mapper 中实现 selectBatchLatestPrices
            try {
                List<SkinPriceHistoryEntity> oldHistoryList = skinPriceHistoryMapper.selectBatchLatestPrices(successDbIds, platformEnum.getName());
                if (CollectionUtil.isNotEmpty(oldHistoryList)) {
                    oldPriceMap = oldHistoryList.stream().collect(Collectors.toMap(SkinPriceHistoryEntity::getSkinId, SkinPriceHistoryEntity::getPrice));
                }
            } catch (Exception e) {
                log.warn("查旧价失败，跳过报警检测");
            }
        }

        List<SkinPriceHistoryEntity> entitiesToSave = new ArrayList<>();

        // 3. 遍历结果
        for (PriceFetchResultDTO dto : results) {
            SkinItemEntity entity = map.get(String.valueOf(dto.getTargetId()));

            if (entity != null) {
                // --- A. 价格预警检测 ---
                BigDecimal currentPrice = dto.getPrice();
                BigDecimal oldPrice = oldPriceMap.get(entity.getId());

                // 只有当有旧价格，且当前价格大于阈值时，才进行波动检测
                if (oldPrice != null && currentPrice.compareTo(minNotifyPrice) > 0) {
                    checkAndNotify(entity, platformEnum.getName(), oldPrice, currentPrice);
                }

                // --- B. 准备入库实体 ---
                SkinPriceHistoryEntity history = new SkinPriceHistoryEntity();
                history.setSkinId(entity.getId()); // 关键：存的是数据库主键
                history.setPlatform(platformEnum.getName()); // 存中文名
                history.setPrice(currentPrice);
                history.setVolume(dto.getVolume());
                history.setCaptureTime(LocalDateTime.now());
                history.setCreatedAt(LocalDateTime.now());

                entitiesToSave.add(history);
            }
        }

        // 4 批量入库
        if (CollectionUtil.isNotEmpty(entitiesToSave)) {
            try {
                priceHistoryService.saveBatch(entitiesToSave);
                log.info("💾 [{}] 成功入库 {} 条", platformEnum.getName(), entitiesToSave.size());
            } catch (Exception e) {
                log.error("❌ [{}] 批量入库失败", platformEnum.getName(), e);
            }
        }
    }
}