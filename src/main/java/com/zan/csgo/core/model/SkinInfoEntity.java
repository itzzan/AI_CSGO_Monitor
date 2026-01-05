package com.zan.csgo.core.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Date;

/**
 * @Author Zan
 * @Create 2026/1/5 10:42
 * @ClassName: SkinInfo
 * @Description : 饰品基本信息表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("skin_info")
public class SkinInfoEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 饰品市场哈希名称
     */
    @TableField("skin_market_hash_name")
    private String skinMarketHashName;

    /**
     * 饰品名称
     */
    @TableField("skin_name")
    private String skinName;

    /**
     * 饰品类型
     */
    @TableField("skin_type")
    private String skinType;

    /**
     * 饰品具体类型（武器）
     */
    @TableField("skin_weapon")
    private String skinWeapon;

    /**
     * 饰品品质
     */
    @TableField("skin_quality")
    private String skinQuality;

    /**
     * 饰品磨损等级
     */
    @TableField("skin_exterior")
    private String skinExterior;

    /**
     * 饰品图片URL
     */
    @TableField("skin_image_url")
    private String skinImageUrl;

    /**
     * 是否是StatTrak暗金，0-否，1-是
     */
    @TableField("is_stattrak")
    private Boolean isStattrak;

    /**
     * 是否是纪念品，0-否，1-是
     */
    @TableField("is_souvenir")
    private Boolean isSouvenir;

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
