package com.zan.csgo.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zan.csgo.enums.DelFlagEnum;
import com.zan.csgo.mapper.SkinItemMapper;
import com.zan.csgo.model.entity.SkinItemEntity;
import com.zan.csgo.service.ISkinItemService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Author Zan
 * @Create 2026/1/6 09:49
 * @ClassName: SkinItemServiceImpl
 * @Description : 饰品服务实现类
 */
@Service
@Slf4j
public class SkinItemServiceImpl extends ServiceImpl<SkinItemMapper, SkinItemEntity> implements ISkinItemService {

    /**
     * 根据饰品ID查询饰品信息
     *
     * @param skinItemId 饰品ID
     * @return 饰品信息
     */
    @Override
    public SkinItemEntity querySkinItemByItemId(Long skinItemId) {
        if (ObjectUtil.isNull(skinItemId)) {
            return null;
        }
        LambdaQueryWrapper<SkinItemEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SkinItemEntity::getSkinItemId, skinItemId);
        wrapper.eq(SkinItemEntity::getDelFlag, DelFlagEnum.NO.getValue());
        return this.getOne(wrapper);
    }

    /**
     * 填充Buff商品ID
     *
     * @param item 饰品信息
     * @return
     */
    @Override
    public boolean fillBuffGoodsId(SkinItemEntity item) {
        if (ObjectUtil.isNull(item)) {
            return false;
        }
        if (ObjectUtil.isNull(item.getBuffGoodsId()) || item.getBuffGoodsId() <= 0) {
            return false;
        }
        LambdaUpdateWrapper<SkinItemEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ObjectUtil.isNotNull(item.getId()), SkinItemEntity::getId, item.getId());
        wrapper.eq(StrUtil.isNotBlank(item.getSkinMarketHashName()), SkinItemEntity::getSkinMarketHashName, item.getSkinMarketHashName());
        wrapper.eq(SkinItemEntity::getDelFlag, DelFlagEnum.NO.getValue());
        wrapper.set(SkinItemEntity::getBuffGoodsId, item.getBuffGoodsId());
        return this.update(wrapper);
    }
}
