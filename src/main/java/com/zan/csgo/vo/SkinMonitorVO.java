package com.zan.csgo.vo;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * @Author Zan
 * @Create 2026/1/6 17:45
 * @ClassName: SkinMonitorVO
 * @Description : 饰品监控结果视图对象
 */
@Data
@Builder
public class SkinMonitorVO {

    // --- 饰品基础信息 ---

    /**
     * 饰品ID
     */
    private Long skinId;

    /**
     * 饰品名称
     */
    private String skinName;

    /**
     * 饰品图片
     */
    private String imageUrl;

    /**
     *饰品的Market Hash Name
     */
    private String marketHashName;

    // --- 各平台数据 (核心变化) ---
    // Key = 平台名称 (BUFF, STEAM)
    // Value = 具体价格信息
    private Map<String, PlatformPriceVO> priceMap;
}
