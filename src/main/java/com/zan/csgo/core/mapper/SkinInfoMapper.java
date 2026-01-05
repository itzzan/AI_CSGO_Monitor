package com.zan.csgo.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zan.csgo.core.model.SkinInfoEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * @Author Zan
 * @Create 2026/1/5 10:50
 * @ClassName: SkinInfoMapper
 * @Description : 饰品基本信息Mapper
 */
@Mapper
public interface SkinInfoMapper extends BaseMapper<SkinInfoEntity> {

    @Select("SELECT DISTINCT skin_market_hash_name FROM skin_info WHERE del_flag = 0")
    List<String> selectAllMarketHashNames();
}
