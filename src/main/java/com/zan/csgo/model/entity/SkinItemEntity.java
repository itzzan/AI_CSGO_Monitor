package com.zan.csgo.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @Author Zan
 * @Create 2026/1/5 17:39
 * @ClassName: SkinItemEntity
 * @Description : 饰品信息表
 */
@Data
@TableName("skin_item")
@EqualsAndHashCode(callSuper = false)
public class SkinItemEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 饰品基础信息ID（唯一）
     */
    @TableField("skin_item_id")
    private Long skinItemId;

    /**
     * 饰品市场哈希名称（唯一）
     */
    @TableField("skin_market_hash_name")
    private String skinMarketHashName;

    /**
     * 饰品名称
     */
    @TableField("skin_name")
    private String skinName;

    /**
     * 饰品大类（手套、匕首、步枪等）
     */
    @TableField("skin_category")
    private String skinCategory;

    /**
     * 饰品具体类型（AK47、M4A1）
     */
    @TableField("skin_weapon")
    private String skinWeapon;

    /**
     * 英文皮肤/图案名（Redline, Doppler, Vanilla）
     */
    @TableField("skin_pattern")
    private String skinPattern;

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
     * 饰品是否暗金，0-否，1-是
     */
    @TableField("is_stattrak")
    private Integer isStattrak;

    /**
     * 饰品是否纪念品，0-否，1-是
     */
    @TableField("is_souvenir")
    private Integer isSouvenir;

    /**
     * 饰品是否带星标(★)，通常为刀/手套，0-否，1-是
     */
    @TableField("is_star")
    private Integer isStar;

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
