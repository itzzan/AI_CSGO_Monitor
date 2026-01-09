package com.zan.csgo.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author Zan
 * @Create 2026/1/8 16:26
 * @ClassName: SkinPriorityEnum
 * @Description : 饰品监控热度优先级
 */
@AllArgsConstructor
@Getter
public enum SkinPriorityEnum {

    COMMON(0, "普通"),

    HOT(1, "热门"),

    ICE(2, "冷门"),

    ;

    private final Integer code;

    private final String desc;
}
