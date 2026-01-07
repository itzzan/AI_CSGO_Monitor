package com.zan.csgo.model.dto;

import lombok.Data;

/**
 * @Author Zan
 * @Create 2026/1/7 11:08
 * @ClassName: JsonSkinPlatformDataDTO
 * @Description : 皮肤平台数据JSON数据对应的实体类
 */
@Data
public class JsonSkinPlatformDataDTO {

    // 这个字段 JSON 里没有，是从 Map 的 Key 拿过来塞进去的
    private String marketHashName;

    // 对应 JSON 中的 "buff163_goods_id"
    // Hutool 解析时，默认支持下划线转驼峰，或者直接用相同字段名
    private Long buff163_goods_id;

    // 其他字段按需添加，比如 youpin_id 等
    // private Integer youpin_id;
}
