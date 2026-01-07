package com.zan.csgo.task;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zan.csgo.enums.DelFlagEnum;
import com.zan.csgo.mapper.SkinItemMapper;
import com.zan.csgo.model.entity.SkinItemEntity;
import com.zan.csgo.service.ISkinMonitorService;
import com.zan.csgo.vo.PlatformPriceVO;
import com.zan.csgo.vo.SkinMonitorVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author Zan
 * @Create 2026/1/7 17:01
 * @ClassName: AutoMonitorTask
 * @Description : 自动化监控任务
 */
//@Component
@Slf4j
public class AutoMonitorTask {

    @Resource
    private SkinItemMapper skinItemMapper;

    @Resource
    private ISkinMonitorService skinMonitorService;

    @Resource(name = "monitorExecutor")
    private ThreadPoolTaskExecutor executor;

    // 🔴 连续失败计数器
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    // 🔴 是否处于熔断冷却中
    private volatile boolean isCoolingDown = false;

    /**
     * 【主任务】每隔 15 分钟执行一次全量扫描
     * fixedDelay = 900000 表示上一次任务结束后，等待 15 分钟再开始下一次（避免任务堆积）
     */
    @Scheduled(fixedDelay = 900000)
    public void startBatchMonitor() {
        if (isCoolingDown) {
            log.warn("❄️ [熔断保护中] 跳过本次全量扫描，等待 IP/账号 解封...");
            return;
        }

        log.info("⏰ [全量监控] 任务开始 (单线程慢速模式)...");

        List<SkinItemEntity> skinList = skinItemMapper.selectList(
                new LambdaQueryWrapper<SkinItemEntity>()
                        .eq(SkinItemEntity::getDelFlag, DelFlagEnum.NO.getValue())
                        .and(qw -> qw
                                .ne(SkinItemEntity::getBuffGoodsId, 0)
                                .or()
                                .ne(SkinItemEntity::getYoupinId, 0))
                        .select(SkinItemEntity::getId, SkinItemEntity::getSkinName)
        );

        if (CollectionUtil.isEmpty(skinList)) {
            return;
        }

        // 提交任务到线程池
        for (SkinItemEntity item : skinList) {
            if (isCoolingDown) {
                log.warn("🛑 任务队列中断停止");
                break;
            }
            executor.submit(() -> processSingleSkin(item));
        }
    }

    /**
     * 单个饰品处理逻辑 (运行在子线程中)
     */
    private void processSingleSkin(SkinItemEntity item) {
        // 双重检查：如果熔断了，直接跳过，不执行
        if (isCoolingDown) {
            return;
        }

        try {
            SkinMonitorVO vo = skinMonitorService.monitorSkin(item.getId());

            if (vo != null) {
                // 检查是否遭遇限流
                boolean limitHit = checkRateLimit(vo);

                if (limitHit) {
                    int failCount = consecutiveFailures.incrementAndGet();
                    log.error("⛔ [触发限流] {} (连续第 {} 次)", item.getSkinName(), failCount);

                    // 🚨 阈值：连续 3 个饰品被限流，立即熔断
                    if (failCount >= 3) {
                        triggerCircuitBreaker();
                    }
                } else {
                    // 只要有一个成功的，重置计数器
                    consecutiveFailures.set(0);
                }
            } else {
                consecutiveFailures.incrementAndGet();
            }

        } catch (Exception e) {
            log.error("❌ [任务异常] {}", e.getMessage());
        } finally {
            // 🔴 关键：执行完一个后，强制休息 5 秒
            // 这是防止封号的最有效手段
            long sleepTime = RandomUtil.randomLong(5000, 5001);
            ThreadUtil.sleep(sleepTime);
        }
    }

    /**
     * 触发熔断：系统暂停 20 分钟
     */
    private void triggerCircuitBreaker() {
        if (isCoolingDown) {
            return;
        }
        isCoolingDown = true;
        log.error("🛑🛑🛑 [严重] 监测到连续限流，系统进入 20分钟 深度冷却模式 🛑🛑🛑");

        // 另起线程倒计时解锁
        new Thread(() -> {
            ThreadUtil.sleep(20 * 60 * 1000); // 睡 20 分钟
            isCoolingDown = false;
            consecutiveFailures.set(0);
            log.info("🟢 [系统恢复] 冷却结束，下一轮任务将正常执行");
        }).start();
    }

    /**
     * 检查返回结果中是否有限流关键词
     */
    private boolean checkRateLimit(SkinMonitorVO vo) {
        if (vo == null || vo.getPriceMap() == null) {
            return false;
        }

        for (Map.Entry<String, PlatformPriceVO> entry : vo.getPriceMap().entrySet()) {
            String msg = entry.getValue().getStatusMsg();
            if (StrUtil.isNotBlank(msg)) {
                // 关键词匹配
                if (msg.contains("429") ||
                        msg.contains("频繁") ||
                        msg.contains("限流") ||
                        msg.contains("拦截") ||
                        msg.contains("Too Many Requests")) {
                    return true; // 只要有一个平台报限流，就算此次任务限流
                }
            }
        }
        return false;
    }
}
