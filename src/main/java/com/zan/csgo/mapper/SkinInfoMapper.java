package com.zan.csgo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zan.csgo.core.model.SkinInfoEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * @Author Zan
 * @Create 2026/1/5 10:50
 * @ClassName: SkinInfoMapper
 * @Description : 饰品基本信息Mapper
 */
@Mapper
public interface SkinInfoMapper extends BaseMapper<SkinInfoEntity> {
}
