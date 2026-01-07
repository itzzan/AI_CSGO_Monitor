package com.zan.csgo.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @Author Zan
 * @Create 2026/1/7 10:33
 * @ClassName: PlatformPriceVO
 * @Description : 单个平台的价格监控结果
 */
@Data
@Builder
public class PlatformPriceVO {
    /**
     * 平台名称 (BUFF, STEAM)
     */
    private String platform;

    /**
     * 最新价格
     */
    private BigDecimal price;

    /**
     * 在售数量/销量
     */
    private Integer volume;

    /**
     * 状态消息 (例如: "映射成功", "抓取失败")
     */
    private String statusMsg;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 该平台对应的商品ID (Buff是goods_id, Steam可能为空)
     * 用于前端跳转链接
     */
    private String targetId;
}
