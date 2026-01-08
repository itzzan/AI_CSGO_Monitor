package com.zan.csgo.task;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zan.csgo.enums.DelFlagEnum;
import com.zan.csgo.enums.SkinPriorityEnum;
import com.zan.csgo.mapper.SkinItemMapper;
import com.zan.csgo.model.entity.SkinItemEntity;
import com.zan.csgo.service.ISkinMonitorService;
import com.zan.csgo.vo.PlatformPriceVO;
import com.zan.csgo.vo.SkinMonitorVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author Zan
 * @Create 2026/1/7 17:01
 * @ClassName: AutoMonitorTask
 * @Description : 自动化监控任务 (单机调度核心)
 */
@Component // 🟢 1. 必须打开这个注解，任务才会启动！
@Slf4j
public class AutoMonitorTask {

    @Resource
    private SkinItemMapper skinItemMapper;

    @Resource
    private ISkinMonitorService skinMonitorService;

    // 注入我们在 ExecutorConfig 配好的单线程池
    @Resource(name = "monitorExecutor")
    private ThreadPoolTaskExecutor executor;

    // 连续失败计数器
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    // 熔断标记
    private volatile boolean isCoolingDown = false;

    /**
     * 🔥 热门队列：每 5 分钟执行一次 (只扫 priority = 1 的)
     */
    @Scheduled(fixedDelay = 300000) // 5分钟
    public void monitorHotItems() {
        log.info("🔥 [热门监控] 开始扫描重点饰品...");
        List<SkinItemEntity> hotList = skinItemMapper.selectList(
                new LambdaQueryWrapper<SkinItemEntity>()
                        .eq(SkinItemEntity::getDelFlag, DelFlagEnum.NO.getValue())
                        .eq(SkinItemEntity::getSkinPriority, SkinPriorityEnum.HOT.getCode()) // 只查热门
        );

        if (hotList.isEmpty()) {
            return;
        }

        // 扔进线程池 (代码同之前)
        hotList.forEach(item -> executor.submit(() -> processSingleSkin(item)));
    }

    /**
     * 🧊 冷门队列：每 2 小时执行一次 (扫 priority = 0 的)
     */
    @Scheduled(fixedDelay = 7200000) // 2小时
    public void monitorColdItems() {
        log.info("🧊 [冷门监控] 开始全量兜底扫描...");
        List<SkinItemEntity> coldList = skinItemMapper.selectList(
                new LambdaQueryWrapper<SkinItemEntity>()
                        .eq(SkinItemEntity::getDelFlag, DelFlagEnum.NO.getValue())
                        .eq(SkinItemEntity::getSkinPriority, SkinPriorityEnum.COMMON.getCode()) // 只查普通
        );

        if (coldList.isEmpty()) {
            return;
        }

        // 扔进线程池
        coldList.forEach(item -> executor.submit(() -> processSingleSkin(item)));
    }

    /**
     * 单个饰品处理逻辑
     */
    private void processSingleSkin(SkinItemEntity item) {
        if (isCoolingDown) {
            return;
        }

        try {
            // 调用核心业务 (这里面会去调用 Strategy -> ProxyProvider)
            SkinMonitorVO vo = skinMonitorService.monitorSkin(item.getId());

            if (vo != null) {
                // 检查结果是否包含“限流”关键字
                boolean limitHit = checkRateLimit(vo);

                if (limitHit) {
                    int failCount = consecutiveFailures.incrementAndGet();
                    log.error("⛔ [触发限流] {} (连续第 {} 次)", item.getSkinName(), failCount);

                    // 🚨 如果连续 3 个饰品（每个饰品重试了5次）都失败，说明 IP 池枯竭或被大规模封锁
                    if (failCount >= 3) {
                        triggerCircuitBreaker();
                    }
                } else {
                    // 只要成功一个，计数器清零
                    consecutiveFailures.set(0);
                }
            } else {
                // 返回空也算失败的一种
                consecutiveFailures.incrementAndGet();
            }

        } catch (Exception e) {
            log.error("❌ [任务异常] ID:{} {}", item.getId(), e.getMessage());
        } finally {
            // 🔴 4. 随机休眠 3~8 秒
            // 之前的 Strategy 内部已经有重试耗时了，这里是“饰品与饰品之间”的间隔
            // 加上这个间隔，让爬虫看起来更像是在慢慢浏览
            long sleepTime = RandomUtil.randomLong(3000, 8000);
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

        // 另起线程倒计时解锁，不占用主线程
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
                // 这些关键词意味着我们的 Strategy 换了 5 个代理都没能成功
                if (msg.contains("429") ||
                        msg.contains("频繁") ||
                        msg.contains("限流") ||
                        msg.contains("拦截") ||
                        msg.contains("重试耗尽")) {
                    return true;
                }
            }
        }
        return false;
    }
}