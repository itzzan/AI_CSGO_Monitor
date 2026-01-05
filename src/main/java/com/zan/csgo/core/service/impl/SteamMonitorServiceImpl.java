package com.zan.csgo.core.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.thread.ThreadUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zan.csgo.config.SteamApiConfig;
import com.zan.csgo.core.api.SteamApiClient;
import com.zan.csgo.core.model.SkinInfoEntity;
import com.zan.csgo.core.model.SkinPriceRecordEntity;
import com.zan.csgo.core.service.ISteamMonitorService;
import com.zan.csgo.core.mapper.SkinInfoMapper;
import com.zan.csgo.core.mapper.SkinPriceRecordMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @Author Zan
 * @Create 2026/1/5 15:05
 * @ClassName: SteamMonitorServiceImpl
 * @Description : TODO 请用一句话描述一下
 */
@Service
@Slf4j
public class SteamMonitorServiceImpl implements ISteamMonitorService {

    @Resource
    private SteamApiClient steamApiClient;

    @Resource
    private SkinInfoMapper skinInfoMapper;

    @Resource
    private SkinPriceRecordMapper skinPriceRecordMapper;

    @Resource
    private SteamApiConfig steamApiConfig;

    private final AtomicBoolean isMonitoring = new AtomicBoolean(false);
    private final Map<String, SkinPriceRecordEntity> latestPrices = new ConcurrentHashMap<>();
    private final Map<String, Integer> monitorStats = new ConcurrentHashMap<>();

    @Override
    public void startMonitoring() {
        if (isMonitoring.compareAndSet(false, true)) {
            log.info("开始Steam饰品监控...");
            monitorAllSkins();
        } else {
            log.warn("监控已经在运行中");
        }
    }

    @Override
    public void stopMonitoring() {
        if (isMonitoring.compareAndSet(true, false)) {
            log.info("停止Steam饰品监控");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SkinPriceRecordEntity monitorSkin(String marketHashName) {
        if (!steamApiConfig.getEnabled()) {
            log.warn("Steam监控未启用");
            return null;
        }

        try {
            // 从Steam获取价格
            SkinPriceRecordEntity priceRecord = steamApiClient.getMarketPrice(
                    marketHashName,
                    steamApiConfig.getCurrency(),
                    steamApiConfig.getAppId()
            );

            if (priceRecord != null) {
                // 计算24小时价格变化
                calculate24hChange(marketHashName, priceRecord);

                // 保存到数据库
                boolean saved = skinPriceRecordMapper.insert(priceRecord) > 0;
                if (saved) {
                    latestPrices.put(marketHashName, priceRecord);
                    updateStats("success", 1);
                    log.debug("监控饰品成功: {}", marketHashName);
                }
                return priceRecord;
            } else {
                updateStats("failed", 1);
                log.warn("获取饰品价格失败: {}", marketHashName);
            }
        } catch (Exception e) {
            updateStats("error", 1);
            log.error("监控饰品异常 - {}: {}", marketHashName, e.getMessage());
        }

        return null;
    }

    @Override
    public Map<String, SkinPriceRecordEntity> batchMonitorSkins(List<String> marketHashNames) {
        Map<String, SkinPriceRecordEntity> results = new HashMap<>();
        AtomicInteger successCount = new AtomicInteger(0);

        marketHashNames.forEach(marketHashName -> {
            try {
                SkinPriceRecordEntity record = monitorSkin(marketHashName);
                if (record != null) {
                    results.put(marketHashName, record);
                    successCount.incrementAndGet();
                }

                // 请求延迟，避免被封
                ThreadUtil.sleep(steamApiConfig.getRateLimitDelay());
            } catch (Exception e) {
                log.error("批量监控异常 - {}: {}", marketHashName, e.getMessage());
            }
        });

        log.info("批量监控完成: 成功={}, 总数={}", successCount.get(), marketHashNames.size());
        return results;
    }

    @Override
    public SkinPriceRecordEntity getLatestPrice(String marketHashName) {
        // 先从缓存获取
        SkinPriceRecordEntity cached = latestPrices.get(marketHashName);
        if (cached != null &&
                DateUtil.betweenMs(cached.getRecordTime(), new Date()) < 5 * 60 * 1000) {
            return cached;
        }

        // 从数据库获取
        SkinPriceRecordEntity record = skinPriceRecordMapper.selectLatestRecord(marketHashName, "Steam");
        if (record != null) {
            latestPrices.put(marketHashName, record);
        }
        return record;
    }

    @Override
    public List<SkinPriceRecordEntity> getPriceHistory(String marketHashName, Date startTime, Date endTime) {
        return skinPriceRecordMapper.selectByMarketHashNameAndTimeRange(
                marketHashName, "Steam", startTime, endTime
        );
    }

    @Override
    public List<SkinPriceRecordEntity> detectPriceAnomalies(BigDecimal threshold) {
        Date twentyFourHoursAgo = DateUtil.offsetHour(new Date(), -24);

        LambdaQueryWrapper<SkinPriceRecordEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SkinPriceRecordEntity::getPlatform, "Steam")
                .ge(SkinPriceRecordEntity::getRecordTime, twentyFourHoursAgo)
                .and(wrapper -> wrapper
                        .ge(SkinPriceRecordEntity::getChange24h, threshold)
                        .or()
                        .le(SkinPriceRecordEntity::getChange24h, threshold.negate()))
                .orderByDesc(SkinPriceRecordEntity::getChange24h)
                .last("LIMIT 50");

        return skinPriceRecordMapper.selectList(queryWrapper);
    }

    @Override
    public List<SkinPriceRecordEntity> detectInventoryAnomalies(Integer threshold) {
        Date twentyFourHoursAgo = DateUtil.offsetHour(new Date(), -24);

        // 获取24小时内的记录，按成交量排序
        LambdaQueryWrapper<SkinPriceRecordEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SkinPriceRecordEntity::getPlatform, "Steam")
                .ge(SkinPriceRecordEntity::getRecordTime, twentyFourHoursAgo)
                .isNotNull(SkinPriceRecordEntity::getVolume24h)
                .ge(SkinPriceRecordEntity::getVolume24h, threshold)
                .orderByDesc(SkinPriceRecordEntity::getVolume24h)
                .last("LIMIT 50");

        return skinPriceRecordMapper.selectList(queryWrapper);
    }

    @Override
    public Map<String, Object> calculatePriceTrend(String marketHashName, Integer days) {
        Date endTime = new Date();
        Date startTime = DateUtil.offsetDay(endTime, -days);

        List<SkinPriceRecordEntity> records = getPriceHistory(marketHashName, startTime, endTime);
        if (CollUtil.isEmpty(records)) {
            return Collections.emptyMap();
        }

        // 按时间排序
        records.sort(Comparator.comparing(SkinPriceRecordEntity::getRecordTime));

        Map<String, Object> trendData = new HashMap<>();
        trendData.put("marketHashName", marketHashName);
        trendData.put("periodDays", days);
        trendData.put("startTime", startTime);
        trendData.put("endTime", endTime);
        trendData.put("recordCount", records.size());

        // 价格统计
        BigDecimal firstPrice = records.get(0).getCurrentPrice();
        BigDecimal lastPrice = records.get(records.size() - 1).getCurrentPrice();
        BigDecimal priceChange = lastPrice.subtract(firstPrice);
        BigDecimal priceChangePercent = priceChange
                .divide(firstPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        trendData.put("firstPrice", firstPrice);
        trendData.put("lastPrice", lastPrice);
        trendData.put("priceChange", priceChange);
        trendData.put("priceChangePercent", priceChangePercent);

        // 计算最高价、最低价、平均价
        BigDecimal totalPrice = BigDecimal.ZERO;
        BigDecimal maxPrice = BigDecimal.ZERO;
        BigDecimal minPrice = new BigDecimal("99999999");
        Integer totalVolume = 0;

        for (SkinPriceRecordEntity record : records) {
            BigDecimal price = record.getCurrentPrice();
            totalPrice = totalPrice.add(price);

            if (price.compareTo(maxPrice) > 0) {
                maxPrice = price;
            }
            if (price.compareTo(minPrice) < 0) {
                minPrice = price;
            }

            if (record.getVolume24h() != null) {
                totalVolume += record.getVolume24h();
            }
        }

        BigDecimal avgPrice = totalPrice.divide(
                BigDecimal.valueOf(records.size()), 2, RoundingMode.HALF_UP);

        trendData.put("maxPrice", maxPrice);
        trendData.put("minPrice", minPrice);
        trendData.put("avgPrice", avgPrice);
        trendData.put("totalVolume", totalVolume);
        trendData.put("avgVolume", totalVolume / records.size());

        // 收集数据点用于图表
        List<Map<String, Object>> dataPoints = records.stream()
                .map(record -> {
                    Map<String, Object> point = new HashMap<>();
                    point.put("time", record.getRecordTime());
                    point.put("price", record.getCurrentPrice());
                    point.put("volume", record.getVolume24h());
                    point.put("listings", record.getSellListings());
                    point.put("change24h", record.getChange24h());
                    return point;
                })
                .collect(Collectors.toList());

        trendData.put("dataPoints", dataPoints);

        return trendData;
    }

    @Override
    public Map<String, Object> getMonitoringStats() {
        Map<String, Object> stats = new HashMap<>(monitorStats);
        stats.put("isMonitoring", isMonitoring.get());
        stats.put("latestPricesCount", latestPrices.size());
        stats.put("lastUpdateTime", new Date());

        // 数据库统计
        Long totalSkins = skinInfoMapper.selectCount(new QueryWrapper<SkinInfoEntity>()
                .eq("del_flag", 0));
        Long totalRecords = skinPriceRecordMapper.selectCount(new QueryWrapper<SkinPriceRecordEntity>()
                .eq("del_flag", 0)
                .eq("platform", "Steam"));

        stats.put("totalSkins", totalSkins);
        stats.put("totalRecords", totalRecords);

        return stats;
    }

    private void monitorAllSkins() {
        if (!isMonitoring.get()) {
            return;
        }

        log.info("开始监控所有饰品...");

        try {
            // 获取需要监控的饰品列表
            List<String> marketHashNames = getSkinsToMonitor();
            log.info("本次监控饰品数量: {}", marketHashNames.size());

            // 批量监控
            batchMonitorSkins(marketHashNames);

            log.info("饰品监控完成");

        } catch (Exception e) {
            log.error("监控所有饰品失败: {}", e.getMessage(), e);
        } finally {
            // 记录监控完成时间
            updateStats("lastCompleteTime", System.currentTimeMillis());
        }
    }

    private List<String> getSkinsToMonitor() {
        Integer maxSkins = steamApiConfig.getMonitor().getMaxSkinsPerRun();

        // 获取所有饰品的市场哈希名称
        List<String> allNames = skinInfoMapper.selectAllMarketHashNames();
        if (CollUtil.isEmpty(allNames)) {
            return Collections.emptyList();
        }

        // 如果饰品数量超过限制，随机选择一部分
        if (allNames.size() > maxSkins) {
            Collections.shuffle(allNames);
            return allNames.subList(0, maxSkins);
        }

        return allNames;
    }

    private void calculate24hChange(String marketHashName, SkinPriceRecordEntity currentRecord) {
        try {
            // 获取24小时前的价格记录
            Date oneDayAgo = DateUtil.offsetDay(new Date(), -1);

            SkinPriceRecordEntity previousRecord = skinPriceRecordMapper.selectLatestRecord(marketHashName, "Steam");

            if (previousRecord != null &&
                    previousRecord.getCurrentPrice() != null &&
                    currentRecord.getCurrentPrice() != null &&
                    previousRecord.getCurrentPrice().compareTo(BigDecimal.ZERO) > 0) {

                BigDecimal oldPrice = previousRecord.getCurrentPrice();
                BigDecimal currentPrice = currentRecord.getCurrentPrice();

                BigDecimal change = currentPrice.subtract(oldPrice)
                        .divide(oldPrice, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100));

                currentRecord.setChange24h(change);
            } else {
                currentRecord.setChange24h(BigDecimal.ZERO);
            }

        } catch (Exception e) {
            log.error("计算24小时价格变化失败: {}", e.getMessage());
            currentRecord.setChange24h(BigDecimal.ZERO);
        }
    }

    private void updateStats(String key, int increment) {
        monitorStats.merge(key, increment, Integer::sum);
    }

    private void updateStats(String key, Object value) {
        monitorStats.put(key, (Integer) value);
    }
}
