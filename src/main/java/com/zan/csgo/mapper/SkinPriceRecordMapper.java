package com.zan.csgo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zan.csgo.core.model.SkinPriceRecordEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author Zan
 * @Create 2026/1/5 10:52
 * @ClassName: SkinPriceRecordMapper
 * @Description : 饰品价格记录Mapper
 */
@Mapper
public interface SkinPriceRecordMapper extends BaseMapper<SkinPriceRecordEntity> {
}
