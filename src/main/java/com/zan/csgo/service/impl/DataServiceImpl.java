package com.zan.csgo.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.zan.csgo.exception.BusinessException;
import com.zan.csgo.model.dto.JsonSkinDataDTO;
import com.zan.csgo.model.entity.SkinItemEntity;
import com.zan.csgo.service.IDataService;
import com.zan.csgo.service.ISkinItemService;
import com.zan.csgo.utils.SkinJsonParserUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * @Author Zan
 * @Create 2026/1/6 09:51
 * @ClassName: DataServiceImpl
 * @Description : 数据服务实现类
 */
@Service
@Slf4j
public class DataServiceImpl implements IDataService {

    @Resource
    private ISkinItemService skinItemService;

    @Resource
    private SkinJsonParserUtil skinJsonParserUtil;

    /**
     * 从JSON文件导入饰品数据
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void importFromJsonFile(String filePath) {
        try {
            // 解析JSON文件
            List<JsonSkinDataDTO> jsonDataList = skinJsonParserUtil.parseJsonFile(filePath);

            if (CollectionUtil.isEmpty(jsonDataList)) {
                throw new BusinessException("JSON文件为空或解析失败");
            }

            log.info("开始导入 {} 条饰品数据", jsonDataList.size());

            // 分批处理
            List<SkinItemEntity> skinItems = jsonDataList.stream()
                    .map(skinJsonParserUtil::convertToSkinItem)
                    .filter(ObjectUtil::isNotNull)
                    .toList();

            int successCount = 0;
            int updateCount = 0;
            int skipCount = 0;

            for (SkinItemEntity skinItem : skinItems) {
                try {
                    // 检查是否已存在
                    SkinItemEntity existing = skinItemService.querySkinItemByItemId(skinItem.getSkinItemId());

                    if (ObjectUtil.isNotNull(existing)) {
                        // 更新现有记录
                        skinItem.setId(existing.getId());
                        boolean updated = skinItemService.updateById(skinItem);
                        if (updated) {
                            updateCount++;
                        } else {
                            log.info("更新饰品失败: {} {}", skinItem.getSkinItemId(), skinItem.getSkinMarketHashName());
                            skipCount++;
                        }
                    } else {
                        // 新增记录
                        boolean saved = skinItemService.save(skinItem);
                        if (saved) {
                            successCount++;
                        } else {
                            log.info("新增饰品失败: {} {}", skinItem.getSkinItemId(), skinItem.getSkinMarketHashName());
                            skipCount++;
                        }
                    }
                } catch (Exception e) {
                    log.error("导入饰品失败: {} {}", skinItem.getSkinItemId(), skinItem.getSkinMarketHashName(), e);
                    skipCount++;
                }
            }

            log.info("饰品数据导入完成，导入结果: 新增 {} 条, 更新 {} 条, 跳过 {} 条", successCount, updateCount, skipCount);
        } catch (Exception e) {
            log.error("导入JSON文件失败: {}", filePath, e);
            throw new BusinessException("导入失败: " + e.getMessage());
        }
    }
}
