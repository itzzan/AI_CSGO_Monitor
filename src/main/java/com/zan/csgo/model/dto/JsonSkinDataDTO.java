package com.zan.csgo.model.dto;

import lombok.Data;

/**
 * @Author Zan
 * @Create 2026/1/5 17:37
 * @ClassName: JsonSkinDataDTO
 * @Description : 饰品JSON数据对应的实体类
 */
@Data
public class JsonSkinDataDTO {

    /**
     * 饰品ID（唯一）
     */
    private String itemId;

    /**
     * 饰品市场Hash名称（唯一）
     */
    private String market_hash_name;

    /**
     * 饰品名称
     */
    private String name;

    /**
     * 饰品图标URL
     */
    private String image_url;
}
