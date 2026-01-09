package com.zan.csgo.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zan.csgo.crawler.strategy.MarketStrategy;
import com.zan.csgo.crawler.strategy.MarketStrategyFactory;
import com.zan.csgo.enums.PlatformEnum;
import com.zan.csgo.mapper.SkinPriceHistoryMapper;
import com.zan.csgo.model.dto.PriceFetchResultDTO;
import com.zan.csgo.model.entity.SkinItemEntity;
import com.zan.csgo.model.entity.SkinPriceHistoryEntity;
import com.zan.csgo.service.INotificationService;
import com.zan.csgo.service.ISkinItemService;
import com.zan.csgo.service.ISkinMonitorService;
import com.zan.csgo.vo.PlatformPriceVO;
import com.zan.csgo.vo.SkinMonitorVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @Author Zan
 * @Create 2026/1/6 17:44
 * @ClassName: SkinMonitorService
 * @Description : 监控饰品价格服务
 */
@Service
@Slf4j
public class SkinMonitorServiceImpl implements ISkinMonitorService {

    @Resource
    private ISkinItemService skinItemService;

    @Resource
    private SkinPriceHistoryMapper priceHistoryMapper;

    @Resource
    private MarketStrategyFactory strategyFactory;

    @Resource
    private INotificationService notificationService;

    /**
     * 执行监控并返回结果 VO
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public SkinMonitorVO monitorSkin(Long skinId) {
        // 1. 基础校验
        SkinItemEntity item = skinItemService.getById(skinId);
        if (item == null) {
            log.warn("ID: {} 对应的饰品不存在", skinId);
            return null;
        }

        // 2. 结果容器
        Map<String, PlatformPriceVO> resultMap = new HashMap<>();

        // =======================================================
        // 模块一：Buff (核心风向标)
        // =======================================================
        // 策略：有ID用ID查，无ID用名字搜
        Object buffKey = (item.getBuffGoodsId() != null && item.getBuffGoodsId() > 0)
                ? item.getBuffGoodsId()
                : item.getSkinMarketHashName();

        PlatformPriceVO buffVO = executeStrategy(PlatformEnum.BUFF, buffKey, item, (result) -> {
            // Buff 特有逻辑：ID自学习回填
            updateSkinIdIfChanged(item, "buff_goods_id", result.getTargetId());
        });
        resultMap.put(PlatformEnum.BUFF.getName(), buffVO);


        // =======================================================
        // 模块二：Steam (基准价格)
        // =======================================================
        // 策略：始终用 HashName 查
        // todo Steam限流比较严重，可以不查，而且Steam比较贵 ，暂不考虑
        //PlatformPriceVO steamVO = executeStrategy(PlatformEnum.STEAM, item.getSkinMarketHashName(), item, null);
        //resultMap.put(PlatformEnum.STEAM.getName(), steamVO);


        // =======================================================
        // 模块三：悠悠有品 (Youpin)
        // =======================================================
        // 策略：只允许用 ID 查 (PC接口限制)
        if (item.getYoupinId() != null && item.getYoupinId() > 0) {
            PlatformPriceVO youpinVO = executeStrategy(PlatformEnum.YOUPIN, item.getYoupinId(), item, null);
            resultMap.put(PlatformEnum.YOUPIN.getName(), youpinVO);
        } else {
            // 无 ID 时的降级处理
            resultMap.put(PlatformEnum.YOUPIN.getName(), PlatformPriceVO.builder()
                    .platform(PlatformEnum.YOUPIN.getName())
                    .success(false)
                    .statusMsg("未关联ID(请同步字典)")
                    .build());
        }

        // =======================================================
        // 3. 组装最终返回
        // =======================================================
        return SkinMonitorVO.builder()
                .skinId(item.getId())
                .skinName(item.getSkinName())
                .imageUrl(item.getSkinImageUrl())
                .marketHashName(item.getSkinMarketHashName())
                .priceMap(resultMap)
                .build();
    }

    /**
     * 🔥 核心通用的策略执行器
     *
     * @param platform  平台枚举
     * @param searchKey 查询Key (可能是ID，也可能是名字)
     * @param item      饰品实体
     * @param onSuccess 成功后的回调 (用于处理各平台特有的逻辑，如ID回填)
     */
    private PlatformPriceVO executeStrategy(PlatformEnum platform, Object searchKey, SkinItemEntity item, Consumer<PriceFetchResultDTO> onSuccess) {
        String platformName = platform.getName();
        try {
            MarketStrategy strategy = strategyFactory.getStrategy(platformName);
            PriceFetchResultDTO result = strategy.fetchPrice(searchKey);

            if (result.isSuccess()) {
                // 1. 计算涨跌幅 & 报警 (必须在入库前做)
                PlatformPriceVO vo = calculateTrendAndBuildVO(item, result, platformName);

                // 2. 执行回调 (如更新 ID)
                if (onSuccess != null) {
                    onSuccess.accept(result);
                }

                // 3. 入库保存历史记录
                savePriceHistory(item.getId(), platformName, result);

                return vo;
            } else {
                return PlatformPriceVO.builder()
                        .platform(platformName)
                        .success(false)
                        .statusMsg(result.getErrorMsg())
                        .build();
            }
        } catch (Exception e) {
            log.error("{} 监控异常", platformName, e);
            return PlatformPriceVO.builder()
                    .platform(platformName)
                    .success(false)
                    .statusMsg("系统异常")
                    .build();
        }
    }

    /**
     * 计算涨跌幅并构建 VO
     */
    private PlatformPriceVO calculateTrendAndBuildVO(SkinItemEntity item, PriceFetchResultDTO result, String platform) {
        BigDecimal currentPrice = result.getPrice();

        // 1. 查数据库获取基准价格
        SkinPriceHistoryEntity history24h = priceHistoryMapper.selectPrice1MinAgo(item.getId(), platform);

        String changeRateStr = "-";
        String changeTag = "";

        // 2. 计算分钟涨跌幅 (vs 1min前)
        if (history24h != null && history24h.getPrice().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal oldPrice = history24h.getPrice();
            BigDecimal diff = currentPrice.subtract(oldPrice);
            // 结果保留4位小数 (0.1234)
            BigDecimal rate = diff.divide(oldPrice, 4, RoundingMode.HALF_UP);
            // 转百分比 (12.34)
            BigDecimal percent = rate.multiply(new BigDecimal(100)).setScale(2, RoundingMode.HALF_UP);

            if (percent.compareTo(BigDecimal.ZERO) > 0) {
                changeRateStr = "+" + percent + "%";
                if (percent.doubleValue() > 10.0) {
                    changeTag = "🔥 暴涨";
                } else if (percent.doubleValue() > 5.0) {
                    changeTag = "📈 大涨";
                }
            } else if (percent.compareTo(BigDecimal.ZERO) < 0) {
                changeRateStr = percent + "%";
                if (percent.doubleValue() < -10.0) {
                    changeTag = "💸 暴跌";
                } else if (percent.doubleValue() < -5.0) {
                    changeTag = "📉 大跌";
                }
            } else {
                changeRateStr = "0.00%";
            }

            // 3. 瞬时波动报警 (vs 上一次)
            if (Math.abs(percent.doubleValue()) > 5.0) { // 波动 > 5%
                log.warn("🚨 [价格异动] {} - {} : {} -> {}", item.getSkinName(), platform, oldPrice, currentPrice);
                // 🔥 接入微信提醒，设定阈值：比如波动绝对值 >= 2% 就发微信
                notificationService.sendPriceAlert(
                        item.getSkinName(),
                        platform,
                        oldPrice,      // 旧价格
                        currentPrice,   // 新价格
                        changeRateStr   // 幅度字符串 (如 "+5.20%")
                );
            }
        }

        return PlatformPriceVO.builder()
                .platform(platform)
                .success(true)
                .price(currentPrice)
                .volume(result.getVolume())
                .changeRate(changeRateStr)
                .changeMsg(changeTag)
                .targetId(result.getTargetId() != null ? result.getTargetId().toString() : null)
                .statusMsg("更新成功")
                .build();
    }

    /**
     * 通用 ID 回填逻辑 (仅当 ID 变化时才更新数据库)
     */
    private void updateSkinIdIfChanged(SkinItemEntity item, String dbColumnName, Object newIdObj) {
        if (newIdObj == null) return;

        try {
            long newId = Long.parseLong(newIdObj.toString());
            Long oldId = null;

            if ("buff_goods_id".equals(dbColumnName)) {
                oldId = item.getBuffGoodsId();
            } else if ("youpin_id".equals(dbColumnName)) {
                oldId = item.getYoupinId();
            }

            // 如果 ID 变了 (或者原来没有)，才执行 SQL
            if (oldId == null || oldId != newId) {
                skinItemService.update(null, new LambdaUpdateWrapper<SkinItemEntity>()
                        .eq(SkinItemEntity::getId, item.getId())
                        .set(StrUtil.equals(dbColumnName, "buff_goods_id"), SkinItemEntity::getBuffGoodsId, newId)
                        .set(StrUtil.equals(dbColumnName, "youpin_id"), SkinItemEntity::getYoupinId, (int) newId)
                );

                // 更新内存中的对象，保证后续流程使用的是最新 ID
                if ("buff_goods_id".equals(dbColumnName)) {
                    item.setBuffGoodsId(newId);
                }

                log.info(">>> [自学习] 饰品 [{}] 更新 {} -> {}", item.getSkinName(), dbColumnName, newId);
            }
        } catch (Exception e) {
            log.warn("ID回填失败", e);
        }
    }

    /**
     * 基础入库方法
     */
    private void savePriceHistory(Long skinId, String platform, PriceFetchResultDTO result) {
        SkinPriceHistoryEntity history = new SkinPriceHistoryEntity();
        history.setSkinId(skinId);
        history.setPlatform(platform);
        history.setPrice(result.getPrice());
        history.setVolume(result.getVolume());
        history.setCreatedAt(LocalDateTime.now());
        priceHistoryMapper.insert(history);
    }
}
