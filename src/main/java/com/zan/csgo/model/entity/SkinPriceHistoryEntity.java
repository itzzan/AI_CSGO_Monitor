package com.zan.csgo.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @Author Zan
 * @Create 2026/1/6 17:35
 * @ClassName: SkinPriceHistoryEntity
 * @Description : 饰品价格历史记录
 */
@Data
@TableName("skin_price_history")
@EqualsAndHashCode(callSuper = false)
public class SkinPriceHistoryEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 饰品ID（对应 skin_item 表的 id）
     */
    @TableField("skin_id")
    private Long skinId;

    /**
     * 平台名称（Buff、Steam）
     */
    @TableField("platform")
    private String platform;

    /**
     * 当前最低售价
     */
    @TableField("price")
    private BigDecimal price;

    /**
     * 最高求购价
     */
    @TableField("buy_order_price")
    private BigDecimal buyOrderPrice;

    /**
     * 当前在售数量
     */
    @TableField("volume")
    private Integer volume;

    /**
     * 抓取时间
     */
    @TableField("capture_time")
    private LocalDateTime captureTime;

    /**
     * 版本号
     */
    @Version
    @TableField("version")
    private Integer version;

    /**
     * 创建人
     */
    @TableField(value = "created_by")
    private Long createdBy;

    /**
     * 创建时间
     */
    @TableField(value = "created_at")
    private LocalDateTime createdAt;

    /**
     * 修改人
     */
    @TableField(value = "changed_by")
    private Long changedBy;

    /**
     * 修改时间
     */
    @TableField(value = "changed_at")
    private LocalDateTime changedAt;

    /**
     * 逻辑删除，0-未删除，1-已删除
     */
    @TableLogic
    @TableField("del_flag")
    private Integer delFlag;
}
