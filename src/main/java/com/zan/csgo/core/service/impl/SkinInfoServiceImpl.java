package com.zan.csgo.core.service.impl;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zan.csgo.core.model.SkinInfoEntity;
import com.zan.csgo.core.service.ISkinInfoService;
import com.zan.csgo.core.mapper.SkinInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @Author Zan
 * @Create 2026/1/5 11:19
 * @ClassName: SkinInfoServiceImpl
 * @Description : 价格基本信息ServiceImpl
 */
@Service
@Slf4j
public class SkinInfoServiceImpl extends ServiceImpl<SkinInfoMapper, SkinInfoEntity> implements ISkinInfoService {

    /**
     * 从JSON文件中批量导入饰品信息
     *
     * @param jsonFilePath
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean loadSkinInfoData(String jsonFilePath) {
        try {
            String jsonStr = FileUtil.readUtf8String(jsonFilePath);
            JSONArray jsonArray = JSONUtil.parseArray(jsonStr);

            List<SkinInfoEntity> skinInfos = new ArrayList<>();
            Date now = new Date();

            for (Object obj : jsonArray) {
                JSONObject jsonObject = (JSONObject) obj;
                SkinInfoEntity skinInfo = convertJsonToSkinInfo(jsonObject);
                skinInfo.setCreatedAt(now);
                skinInfo.setChangedAt(now);
                skinInfos.add(skinInfo);
            }

            // 批量保存或更新
            return this.saveOrUpdateBatch(skinInfos);
        } catch (Exception e) {
            log.error("批量导入饰品信息失败: {}", e.getMessage(), e);
            return false;
        }
    }

    private SkinInfoEntity convertJsonToSkinInfo(JSONObject jsonObject) {
        SkinInfoEntity skinInfo = new SkinInfoEntity();
        skinInfo.setSkinMarketHashName(jsonObject.getStr("market_hash_name"));
        skinInfo.setSkinName(jsonObject.getStr("name"));
        skinInfo.setSkinImageUrl(jsonObject.getStr("image_url"));

        // 从market_hash_name中解析其他信息
        String marketHashName = skinInfo.getSkinMarketHashName();
        parseSkinAttributes(skinInfo, marketHashName);

        return skinInfo;
    }

    private void parseSkinAttributes(SkinInfoEntity skinInfo, String marketHashName) {
        // 解析是否纪念品
        skinInfo.setIsSouvenir(marketHashName.toLowerCase().contains("souvenir"));

        // 解析是否暗金
        skinInfo.setIsStattrak(marketHashName.toLowerCase().contains("stattrak"));

        // 这里可以根据market_hash_name的格式进一步解析其他属性
        // 例如: "★ StatTrak™ M9 Bayonet | Doppler (Factory New)"
        // 可以解析出类型、武器、品质、磨损等

        // 简化实现，后续可以完善
        if (marketHashName.contains("|")) {
            String[] parts = marketHashName.split("\\|");
            if (parts.length > 1) {
                // 可以解析皮肤名称和磨损
                String weaponPart = parts[0].trim();
                String exteriorPart = parts[1].trim();

                // 设置武器类型
                if (weaponPart.contains("★")) {
                    // 匕首
                    skinInfo.setSkinType("Knife");
                    skinInfo.setSkinWeapon(weaponPart.replace("★", "").trim());
                } else if (weaponPart.contains("Gloves")) {
                    // 手套
                    skinInfo.setSkinType("Gloves");
                    skinInfo.setSkinWeapon("Gloves");
                } else {
                    skinInfo.setSkinWeapon(weaponPart);
                }

                // 解析磨损
                if (exteriorPart.contains("Factory New")) {
                    // 崭新出场
                    skinInfo.setSkinExterior("Factory New");
                } else if (exteriorPart.contains("Minimal Wear")) {
                    // 略有磨损
                    skinInfo.setSkinExterior("Minimal Wear");
                } else if (exteriorPart.contains("Field-Tested")) {
                    // 久经沙场
                    skinInfo.setSkinExterior("Field-Tested");
                } else if (exteriorPart.contains("Well-Worn")) {
                    // 破损不堪
                    skinInfo.setSkinExterior("Well-Worn");
                } else if (exteriorPart.contains("Battle-Scarred")) {
                    // 战痕累累
                    skinInfo.setSkinExterior("Battle-Scarred");
                }
            }
        }
    }
}
