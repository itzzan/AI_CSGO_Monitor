package com.zan.csgo.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.zan.csgo.crawler.strategy.MarketStrategy;
import com.zan.csgo.crawler.strategy.MarketStrategyFactory;
import com.zan.csgo.enums.PlatformEnum;
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

        // 模块一：执行 Buff 监控逻辑
        try {
            // 1.1 从工厂获取 Buff 策略
            MarketStrategy buffStrategy = strategyFactory.getStrategy(PlatformEnum.BUFF.getName());

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
            resultMap.put(PlatformEnum.BUFF.getName(), buffVO);

        } catch (Exception e) {
            log.error("Buff 监控任务异常", e);
            resultMap.put(PlatformEnum.BUFF.getName(), PlatformPriceVO.builder()
                    .platform(PlatformEnum.BUFF.getName())
                    .success(false)
                    .statusMsg("系统异常: " + e.getMessage())
                    .build());
        }

        // 模块二：执行 Steam 监控逻辑
        processPlatform(item, PlatformEnum.STEAM.getName(), item.getSkinMarketHashName(), resultMap);

        // 模块三：执行 悠悠有品 监控逻辑
        try {
            Long youpinId = item.getYoupinId();
            if (youpinId != null && youpinId > 0) {
                // 有 ID，直接走 PC 接口查价
                MarketStrategy youpinStrategy = strategyFactory.getStrategy(PlatformEnum.YOUPIN.getName());
                PriceFetchResultDTO youpinRes = youpinStrategy.fetchPrice(youpinId);
                resultMap.put(PlatformEnum.YOUPIN.getName(), handlePlatformResult(item, youpinRes, PlatformEnum.YOUPIN.getName()));
            } else {
                // 无 ID，返回提示状态，引导用户去同步字典
                PlatformPriceVO failVO = PlatformPriceVO.builder()
                        .platform(PlatformEnum.YOUPIN.getName())
                        .success(false)
                        .statusMsg("未关联ID")
                        .build();
                resultMap.put(PlatformEnum.YOUPIN.getName(), failVO);
            }
        } catch (Exception e) {
            log.error("Youpin 监控异常", e);
            PlatformPriceVO failVO = PlatformPriceVO.builder()
                    .platform(PlatformEnum.YOUPIN.getName())
                    .success(false)
                    .statusMsg("系统异常")
                    .build();
            resultMap.put(PlatformEnum.YOUPIN.getName(), failVO);
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
        String statusMsg = "";
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
                    boolean b = skinItemService.fillBuffGoodsIdAndYoupinId(item);
                    if (b) {
                        statusMsg = "映射建立成功，价格已更新";
                        log.info(">>> [自学习] 饰品 [{}] 更新 BuffID 成功: {}", item.getSkinName(), fetchedId);
                    } else {
                        log.warn(">>> [自学习] 饰品 [{}] 更新 BuffID 失败", item.getSkinName());
                    }
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
     * 通用平台处理方法 (Steam, C5, IGXE 等通用逻辑)
     */
    private void processPlatform(SkinItemEntity item, String platformName, Object key, Map<String, PlatformPriceVO> resultMap) {
        try {
            // 从工厂获取 Steam / C5 / IGXE 策略
            MarketStrategy strategy = strategyFactory.getStrategy(platformName);

            // 执行抓取 (Steam 只需要 HashName)
            PriceFetchResultDTO result = strategy.fetchPrice(key);

            // 如果成功，保存历史价格
            if (result.isSuccess()) {
                savePriceHistory(item.getId(), platformName, result);

                // 如果是 Youpin/C5 返回了 ID，这里可以像 Buff 那样做自学习入库逻辑
                // updatePlatformId(item, platformName, result.getTargetId());
            }

            // 构建 Steam 的 VO
            resultMap.put(platformName, PlatformPriceVO.builder()
                    .platform(platformName)
                    .success(result.isSuccess())
                    .price(result.getPrice())
                    .volume(result.getVolume())
                    .statusMsg(result.isSuccess() ? "更新成功" : result.getErrorMsg())
                    .targetId(result.getTargetId() != null ? result.getTargetId().toString() : null)
                    .build());

        } catch (Exception e) {
            log.error("{} 监控异常", platformName, e);
            resultMap.put(platformName, PlatformPriceVO.builder().success(false).statusMsg("跳过").build());
        }
    }

    /**
     * 通用处理：保存历史价格 + 构建 VO
     */
    private PlatformPriceVO handlePlatformResult(SkinItemEntity item, PriceFetchResultDTO result, String platform) {
        if (result.isSuccess()) {
            // 异步或同步保存价格历史 (建议量大时用消息队列)
            savePriceHistory(item.getId(), platform, result);
        }

        return PlatformPriceVO.builder()
                .platform(platform)
                .success(result.isSuccess())
                .price(result.getPrice())
                .volume(result.getVolume())
                .targetId(result.getTargetId() != null ? result.getTargetId().toString() : null)
                .statusMsg(result.isSuccess() ? "更新成功" : result.getErrorMsg())
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
