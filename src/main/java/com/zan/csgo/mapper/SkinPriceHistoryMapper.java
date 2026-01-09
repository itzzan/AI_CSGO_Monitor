package com.zan.csgo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zan.csgo.model.entity.SkinPriceHistoryEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * @Author Zan
 * @Create 2026/1/6 17:39
 * @ClassName: SkinPriceHistoryMapper
 * @Description : 饰品皮肤价格历史 Mapper
 */
public interface SkinPriceHistoryMapper extends BaseMapper<SkinPriceHistoryEntity> {

    /**
     * 查询某饰品在某平台的最新一条价格记录（排除当前刚插入的那条）
     * 用于计算：环比波动（较上一次）
     */
    @Select("SELECT * FROM skin_price_history " +
            "WHERE skin_id = #{skinId} AND platform = #{platform} " +
            "ORDER BY created_at DESC LIMIT 1")
    SkinPriceHistoryEntity selectLatestPrice(@Param("skinId") Long skinId, @Param("platform") String platform);

    /**
     * 查询 24小时前 附近的一条价格记录
     * 用于计算：日涨跌幅
     */
    @Select("SELECT * FROM skin_price_history " +
            "WHERE skin_id = #{skinId} AND platform = #{platform} " +
            "AND created_at <= DATE_SUB(NOW(), INTERVAL 24 HOUR) " +
            "ORDER BY created_at DESC LIMIT 1")
    SkinPriceHistoryEntity selectPrice24hAgo(@Param("skinId") Long skinId, @Param("platform") String platform);

    /**
     * 查 1分钟前 的记录 (用于计算实时涨跌)
     * 逻辑：查找 created_at <= (当前时间 - 1分钟) 的最新一条
     */
    @Select("SELECT * FROM skin_price_history " +
            "WHERE skin_id = #{skinId} AND platform = #{platform} " +
            "AND created_at <= DATE_SUB(NOW(), INTERVAL 1 MINUTE) " +
            "ORDER BY created_at DESC LIMIT 1")
    SkinPriceHistoryEntity selectPrice1MinAgo(@Param("skinId") Long skinId, @Param("platform") String platform);

    /**
     * 批量查询某平台上一轮的最新价格 (用于对比波动)
     */
    List<SkinPriceHistoryEntity> selectBatchLatestPrices(@Param("ids") Collection<Long> ids, @Param("platform") String platform);
}
