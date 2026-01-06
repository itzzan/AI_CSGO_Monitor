package com.zan.csgo.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * @Author Zan
 * @Create 2026/1/6 10:03
 * @ClassName: DelFlagEnum
 * @Description : 删除标识枚举
 */
@AllArgsConstructor
@Getter
public enum DelFlagEnum {

    NO(0, "否"),

    YES(1, "是");

    private final Integer value;

    private final String description;

    public static String getDescriptionByValue(Integer value) {
        return Arrays.stream(values()).filter(x -> Objects.equals(x.getValue(), value)).findFirst()
                .map(DelFlagEnum::getDescription).orElseThrow(() -> new NoSuchElementException("没有找到对应的枚举！"));
    }

    public static boolean isYes(Integer value) {
        return Objects.equals(YES.getValue(), value);
    }

}
