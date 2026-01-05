package com.zan.csgo.core.task;

import com.zan.csgo.core.service.ISteamMonitorService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @Author Zan
 * @Create 2026/1/5 15:08
 * @ClassName: SteamMonitorTask
 * @Description : Steam监控定时任务
 */
@Component
@Slf4j
public class SteamMonitorTask {

    @Resource
    private ISteamMonitorService steamMonitorService;

    /**
     * 定时监控任务 - 每5分钟执行
     */
    @Scheduled(cron = "${csgo.steam.monitor.cron:0 */5 * * * ?}")
    public void executeMonitor() {
        log.info("执行Steam饰品监控定时任务...");

        try {
            steamMonitorService.startMonitoring();
        } catch (Exception e) {
            log.error("Steam监控任务执行失败: {}", e.getMessage(), e);
        }
    }
}
