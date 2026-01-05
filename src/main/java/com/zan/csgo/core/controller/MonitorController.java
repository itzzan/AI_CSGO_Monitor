package com.zan.csgo.core.controller;

import cn.hutool.core.date.DateUtil;
import com.zan.common.Result;
import com.zan.csgo.core.model.SkinPriceRecordEntity;
import com.zan.csgo.core.service.ISteamMonitorService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @Author Zan
 * @Create 2026/1/5 15:11
 * @ClassName: MonitorController
 * @Description : TODO 请用一句话描述一下
 */
@Slf4j
@RestController
@RequestMapping("/monitor")
public class MonitorController {

    @Resource
    private ISteamMonitorService steamMonitorService;

    /**
     * 开始监控
     */
    @PostMapping("/start")
    public Result<Boolean> startMonitor() {
        try {
            steamMonitorService.startMonitoring();
            return Result.success(true);
        } catch (Exception e) {
            log.error("开始监控失败: {}", e.getMessage(), e);
            return Result.failed("开始监控失败: " + e.getMessage());
        }
    }

    /**
     * 停止监控
     */
    @PostMapping("/stop")
    public Result<Boolean> stopMonitor() {
        try {
            steamMonitorService.stopMonitoring();
            return Result.success(true);
        } catch (Exception e) {
            log.error("停止监控失败: {}", e.getMessage(), e);
            return Result.failed("停止监控失败: " + e.getMessage());
        }
    }

    /**
     * 监控单个饰品
     */
    @PostMapping("/skin/{marketHashName}")
    public Result<SkinPriceRecordEntity> monitorSkin(@PathVariable String marketHashName) {
        try {
            SkinPriceRecordEntity record = steamMonitorService.monitorSkin(marketHashName);
            if (record == null) {
                return Result.failed("监控失败");
            }
            return Result.success(record);
        } catch (Exception e) {
            log.error("监控饰品失败: {}", e.getMessage(), e);
            return Result.failed("监控失败: " + e.getMessage());
        }
    }

    /**
     * 获取饰品最新价格
     */
    @GetMapping("/price/latest/{marketHashName}")
    public Result<SkinPriceRecordEntity> getLatestPrice(@PathVariable String marketHashName) {
        try {
            SkinPriceRecordEntity record = steamMonitorService.getLatestPrice(marketHashName);
            if (record == null) {
                return Result.failed("暂无价格数据");
            }
            return Result.success(record);
        } catch (Exception e) {
            log.error("获取最新价格失败: {}", e.getMessage(), e);
            return Result.failed("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取价格历史
     */
    @GetMapping("/price/history/{marketHashName}")
    public Result<List<SkinPriceRecordEntity>> getPriceHistory(@PathVariable String marketHashName, @RequestParam(defaultValue = "7") Integer days) {
        try {
            Date endTime = new Date();
            Date startTime = DateUtil.offsetDay(endTime, -days);

            List<SkinPriceRecordEntity> records = steamMonitorService.getPriceHistory(
                    marketHashName, startTime, endTime);

            return Result.success(records);
        } catch (Exception e) {
            log.error("获取价格历史失败: {}", e.getMessage(), e);
            return Result.failed("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取价格趋势分析
     */
    @GetMapping("/price/trend/{marketHashName}")
    public Result<Map<String, Object>> getPriceTrend(@PathVariable String marketHashName, @RequestParam(defaultValue = "7") Integer days) {
        try {
            Map<String, Object> trend = steamMonitorService.calculatePriceTrend(marketHashName, days);
            if (trend.isEmpty()) {
                return Result.failed("暂无趋势数据");
            }
            return Result.success(trend);
        } catch (Exception e) {
            log.error("获取价格趋势失败: {}", e.getMessage(), e);
            return Result.failed("获取失败: " + e.getMessage());
        }
    }

    /**
     * 检测价格异常
     */
    @GetMapping("/anomalies/price")
    public Result<List<SkinPriceRecordEntity>> detectPriceAnomalies(@RequestParam(required = false) BigDecimal threshold) {
        try {
            BigDecimal actualThreshold = threshold != null ? threshold : new BigDecimal("10.0");
            List<SkinPriceRecordEntity> anomalies = steamMonitorService.detectPriceAnomalies(actualThreshold);
            return Result.success(anomalies);
        } catch (Exception e) {
            log.error("检测价格异常失败: {}", e.getMessage(), e);
            return Result.failed("检测失败: " + e.getMessage());
        }
    }

    /**
     * 检测库存异常
     */
    @GetMapping("/anomalies/inventory")
    public Result<List<SkinPriceRecordEntity>> detectInventoryAnomalies(@RequestParam(defaultValue = "100") Integer threshold) {
        try {
            List<SkinPriceRecordEntity> anomalies = steamMonitorService.detectInventoryAnomalies(threshold);
            return Result.success(anomalies);
        } catch (Exception e) {
            log.error("检测库存异常失败: {}", e.getMessage(), e);
            return Result.failed("检测失败: " + e.getMessage());
        }
    }

    /**
     * 获取监控统计
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getMonitoringStats() {
        try {
            Map<String, Object> stats = steamMonitorService.getMonitoringStats();
            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取监控统计失败: {}", e.getMessage(), e);
            return Result.failed("获取失败: " + e.getMessage());
        }
    }

    /**
     * 批量监控饰品
     */
    @PostMapping("/batch")
    public Result<Map<String, SkinPriceRecordEntity>> batchMonitor(@RequestBody List<String> marketHashNames) {
        try {
            Map<String, SkinPriceRecordEntity> results = steamMonitorService.batchMonitorSkins(marketHashNames);
            return Result.success(results);
        } catch (Exception e) {
            log.error("批量监控失败: {}", e.getMessage(), e);
            return Result.failed("批量监控失败: " + e.getMessage());
        }
    }
}
