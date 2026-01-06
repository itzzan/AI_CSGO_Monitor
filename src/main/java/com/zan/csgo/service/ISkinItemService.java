package com.zan.csgo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zan.csgo.model.entity.SkinItemEntity;

/**
 * @Author Zan
 * @Create 2026/1/5 18:04
 * @ClassName: ISkinItemService
 * @Description : 饰品服务接口
 */
public interface ISkinItemService extends IService<SkinItemEntity> {

    SkinItemEntity querySkinItemByItemId(Long skinItemId);
}
