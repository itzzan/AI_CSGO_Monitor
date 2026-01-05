package com.zan.csgo.core.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zan.csgo.core.model.SkinInfoEntity;
import com.zan.csgo.core.service.ISkinInfoService;
import com.zan.csgo.mapper.SkinInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @Author Zan
 * @Create 2026/1/5 11:19
 * @ClassName: SkinInfoServiceImpl
 * @Description : 价格基本信息ServiceImpl
 */
@Service
@Slf4j
public class SkinInfoServiceImpl extends ServiceImpl<SkinInfoMapper, SkinInfoEntity> implements ISkinInfoService {
}
