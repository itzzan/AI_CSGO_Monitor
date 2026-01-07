package com.zan.csgo.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.zan.csgo.crawler.strategy.MarketStrategy;
import com.zan.csgo.crawler.strategy.MarketStrategyFactory;
import com.zan.csgo.mapper.SkinPriceHistoryMapper;
import com.zan.csgo.model.dto.PriceFetchResultDTO;
import com.zan.csgo.model.entity.SkinItemEntity;
import com.zan.csgo.model.entity.SkinPriceHistoryEntity;
import com.zan.csgo.service.ISkinItemService;
import com.zan.csgo.service.ISkinMonitorService;
import com.zan.csgo.vo.PlatformPriceVO;
import com.zan.csgo.vo.SkinMonitorVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * @Author Zan
 * @Create 2026/1/6 17:44
 * @ClassName: SkinMonitorService
 * @Description : 监控饰品价格服务
 */
@Service
@Slf4j
public class SkinMonitorService implements ISkinMonitorService {

    @Resource
    private ISkinItemService skinItemService;

    @Resource
    private SkinPriceHistoryMapper priceHistoryMapper;

    @Resource
    private MarketStrategyFactory strategyFactory;

    /**
     * 执行监控并返回结果 VO
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public SkinMonitorVO monitorSkin(Long skinId) {
        // 1. 从数据库查询饰品基础信息
        SkinItemEntity item = skinItemService.getById(skinId);
        if (item == null) {
            log.warn("ID: {} 对应的饰品不存在", skinId);
            return null; // 或者抛出自定义业务异常
        }

        // 2. 准备结果容器 (Key=平台名, Value=价格信息)
        Map<String, PlatformPriceVO> resultMap = new HashMap<>();

        // =======================================================
        // 模块一：执行 Buff 监控逻辑
        // =======================================================
        try {
            // 1.1 从工厂获取 Buff 策略
            MarketStrategy buffStrategy = strategyFactory.getStrategy("BUFF");

            // 1.2 智能决定参数 (有 ID 传 ID，没 ID 传 HashName)
            Object buffKey;
            Long currentDbId = item.getBuffGoodsId();
            if (ObjectUtil.isNotNull(currentDbId) && currentDbId > 0) {
                buffKey = currentDbId; // 性能模式：直接 ID 查价
            } else {
                buffKey = item.getSkinMarketHashName(); // 初始化模式：搜索查价
            }

            // 1.3 执行抓取
            PriceFetchResultDTO buffResult = buffStrategy.fetchPrice(buffKey);

            // 1.4 处理 Buff 结果 (入库 + ID 自学习)
            PlatformPriceVO buffVO = handleBuffResult(item, buffResult);

            // 1.5 放入结果集
            resultMap.put("BUFF", buffVO);

        } catch (Exception e) {
            log.error("Buff 监控任务异常", e);
            resultMap.put("BUFF", PlatformPriceVO.builder()
                    .platform("BUFF")
                    .success(false)
                    .statusMsg("系统异常: " + e.getMessage())
                    .build());
        }

        // =======================================================
        // 模块二：执行 Steam 监控逻辑
        // =======================================================
        try {
            // 2.1 从工厂获取 Steam 策略
            MarketStrategy steamStrategy = strategyFactory.getStrategy("STEAM");

            // 2.2 执行抓取 (Steam 只需要 HashName)
            PriceFetchResultDTO steamResult = steamStrategy.fetchPrice(item.getSkinMarketHashName());

            // 2.3 如果成功，保存历史价格
            if (steamResult.isSuccess()) {
                savePriceHistory(item.getId(), "STEAM", steamResult);
            }

            // 2.4 构建 Steam 的 VO
            PlatformPriceVO steamVO = PlatformPriceVO.builder()
                    .platform("STEAM")
                    .success(steamResult.isSuccess())
                    .price(steamResult.getPrice())
                    .volume(steamResult.getVolume())
                    .statusMsg(steamResult.isSuccess() ? "更新成功" : steamResult.getErrorMsg())
                    .build();

            // 2.5 放入结果集
            resultMap.put("STEAM", steamVO);

        } catch (Exception e) {
            log.error("Steam 监控任务异常", e);
            resultMap.put("STEAM", PlatformPriceVO.builder()
                    .platform("STEAM")
                    .success(false)
                    .statusMsg("系统异常")
                    .build());
        }

        // =======================================================
        // 3. 组装最终的大 VO 返回给前端
        // =======================================================
        return SkinMonitorVO.builder()
                .skinId(item.getId())
                .skinName(item.getSkinName())
                .imageUrl(item.getSkinImageUrl())          // 假设实体类有这个字段
                .marketHashName(item.getSkinMarketHashName())
                .priceMap(resultMap)                       // 放入各平台数据
                .build();
    }

    /**
     * 私有辅助方法：处理 Buff 的复杂逻辑 (价格入库 + ID自学习)
     */
    private PlatformPriceVO handleBuffResult(SkinItemEntity item, PriceFetchResultDTO result) {
        String statusMsg;
        String targetIdStr = null;

        if (result.isSuccess()) {
            // A. 保存价格历史
            savePriceHistory(item.getId(), "BUFF", result);

            // B. 处理 ID 自学习 (核心逻辑)
            if (result.getTargetId() != null) {
                // 安全转换为 long (防止 Integer/Long 类型不一致报错)
                long fetchedId = Long.parseLong(result.getTargetId().toString());
                targetIdStr = String.valueOf(fetchedId);

                Long currentDbId = item.getBuffGoodsId();

                // 如果数据库是空的，或者 ID 变了 -> 执行更新
                if (currentDbId == null || currentDbId != fetchedId) {
                    item.setBuffGoodsId(fetchedId);
                    skinItemService.fillBuffGoodsId(item);
                    statusMsg = "映射建立成功，价格已更新";
                    log.info(">>> [自学习] 饰品 [{}] 更新 BuffID: {}", item.getSkinName(), fetchedId);
                } else {
                    statusMsg = "价格刷新成功";
                }
            } else {
                statusMsg = "价格刷新成功(未返回ID)";
            }
        } else {
            statusMsg = "抓取失败: " + result.getErrorMsg();
        }

        // C. 构建并返回 Buff 的 VO
        return PlatformPriceVO.builder()
                .platform("BUFF")
                .success(result.isSuccess())
                .price(result.getPrice())
                .volume(result.getVolume())
                .targetId(targetIdStr) // 返回给前端 ID，方便做跳转链接
                .statusMsg(statusMsg)
                .build();
    }

    /**
     * 私有辅助方法：通用价格入库逻辑
     */
    private void savePriceHistory(Long skinId, String platform, PriceFetchResultDTO result) {
        SkinPriceHistoryEntity history = new SkinPriceHistoryEntity();
        history.setSkinId(skinId);
        history.setPlatform(platform); // BUFF 或 STEAM
        history.setPrice(result.getPrice());
        history.setVolume(result.getVolume());
        history.setCreatedAt(LocalDateTime.now());

        // 插入数据库
        priceHistoryMapper.insert(history);
    }
}
