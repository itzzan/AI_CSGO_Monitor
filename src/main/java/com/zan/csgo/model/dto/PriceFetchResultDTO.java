package com.zan.csgo.model.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * @Author Zan
 * @Create 2026/1/6 17:54
 * @ClassName: PriceFetchResultDTO
 * @Description : 价格抓取结果 DTO
 */
@Data
@Builder
public class PriceFetchResultDTO {

    /**
     * 是否抓取成功
     */
    private boolean success;

    /**
     * 最低价
     */
    private BigDecimal price;

    /**
     * 销量/在售数量
     */
    private Integer volume;

    /**
     * 平台：STEAM, BUFF
     */
    private String platform;

    /**
     * 如果失败，错误原因
     */
    private String errorMsg;

    /**
     * 回传抓取对应的商品ID
     */
    private Object targetId;

    // 静态构造失败的方法
    public static PriceFetchResultDTO fail(String platform, String msg) {
        return PriceFetchResultDTO.builder()
                .success(false)
                .platform(platform)
                .errorMsg(msg)
                .build();
    }
}
