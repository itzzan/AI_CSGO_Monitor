package com.zan.csgo.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author Zan
 * @Create 2026/1/7 11:27
 * @ClassName: PlatformEnum
 * @Description : 各大平台枚举
 */
@AllArgsConstructor
@Getter
public enum PlatformEnum {

    STEAM(1, "Steam"),

    BUFF(2, "网易Buff"),

    YOUPIN(3, "悠悠有品"),

    C5(4, "C5GAME"),

    IGXE(5, "IGXE"),

    ;

    private final Integer code;

    private final String name;
}
