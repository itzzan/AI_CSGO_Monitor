package com.zan.csgo.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zan.csgo.core.model.SkinPriceRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

/**
 * @Author Zan
 * @Create 2026/1/5 10:52
 * @ClassName: SkinPriceRecordMapper
 * @Description : 饰品价格记录Mapper
 */
@Mapper
public interface SkinPriceRecordMapper extends BaseMapper<SkinPriceRecordEntity> {

    @Select("SELECT * FROM skin_price_record WHERE skin_market_hash_name = #{marketHashName} " +
            "AND platform = #{platform} AND record_time >= #{startTime} AND record_time <= #{endTime} " +
            "AND del_flag = 0 ORDER BY record_time DESC")
    List<SkinPriceRecordEntity> selectByMarketHashNameAndTimeRange(
            @Param("marketHashName") String marketHashName,
            @Param("platform") String platform,
            @Param("startTime") Date startTime,
            @Param("endTime") Date endTime);

    @Select("SELECT * FROM skin_price_record WHERE skin_market_hash_name = #{marketHashName} " +
            "AND platform = #{platform} AND del_flag = 0 ORDER BY record_time DESC LIMIT 1")
    SkinPriceRecordEntity selectLatestRecord(
            @Param("marketHashName") String marketHashName,
            @Param("platform") String platform);
}
