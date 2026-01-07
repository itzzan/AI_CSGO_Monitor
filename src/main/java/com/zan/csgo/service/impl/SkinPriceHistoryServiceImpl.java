package com.zan.csgo.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zan.csgo.mapper.SkinPriceHistoryMapper;
import com.zan.csgo.model.entity.SkinPriceHistoryEntity;
import com.zan.csgo.service.ISkinPriceHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Author Zan
 * @Create 2026/1/6 17:42
 * @ClassName: SkinPriceHistoryServiceImpl
 * @Description : 饰品皮肤价格历史服务实现类
 */
@Service
@Slf4j
public class SkinPriceHistoryServiceImpl extends ServiceImpl<SkinPriceHistoryMapper, SkinPriceHistoryEntity> implements ISkinPriceHistoryService {
}
