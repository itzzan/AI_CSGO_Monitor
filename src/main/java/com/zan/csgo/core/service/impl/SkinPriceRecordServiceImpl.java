package com.zan.csgo.core.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zan.csgo.core.model.SkinPriceRecordEntity;
import com.zan.csgo.core.service.ISkinPriceRecordService;
import com.zan.csgo.core.mapper.SkinPriceRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Author Zan
 * @Create 2026/1/5 11:20
 * @ClassName: SkinPriceRecordServiceImpl
 * @Description : 饰品价格记录ServiceImpl
 */
@Service
@Slf4j
public class SkinPriceRecordServiceImpl extends ServiceImpl<SkinPriceRecordMapper, SkinPriceRecordEntity> implements ISkinPriceRecordService {
}
