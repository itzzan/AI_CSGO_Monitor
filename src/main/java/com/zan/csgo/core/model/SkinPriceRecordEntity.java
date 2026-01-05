package com.zan.csgo.core.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * @Author Zan
 * @Create 2026/1/5 10:48
 * @ClassName: SkinPriceRecord
 * @Description : 饰品价格记录表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("skin_price_record")
public class SkinPriceRecordEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("skin_market_hash_name")
    private String skinMarketHashName;

    @TableField("platform")
    private String platform;

    @TableField("record_time")
    private Date recordTime;

    @TableField("current_price")
    private BigDecimal currentPrice;

    @TableField("lowest_price")
    private BigDecimal lowestPrice;

    @TableField("highest_price")
    private BigDecimal highestPrice;

    @TableField("sell_listings")
    private Integer sellListings;

    @TableField("volume_24h")
    private Integer volume24h;

    @TableField("change_24h")
    private BigDecimal change24h;

    @Version
    @TableField("version")
    private Integer version;

    @TableField("created_by")
    private Long createdBy;

    @TableField("created_at")
    private Date createdAt;

    @TableField("changed_by")
    private Long changedBy;

    @TableField(value = "changed_at")
    private Date changedAt;

    @TableLogic
    @TableField("del_flag")
    private Boolean delFlag;
}
