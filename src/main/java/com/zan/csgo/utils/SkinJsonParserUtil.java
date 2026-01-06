package com.zan.csgo.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.google.common.collect.Lists;
import com.zan.csgo.model.dto.JsonSkinDataDTO;
import com.zan.csgo.model.entity.SkinItemEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author Zan
 * @Create 2026/1/6 09:26
 * @ClassName: SkinJsonParserUtil
 * @Description : 饰品JSON数据解析器
 */
@Component
@Slf4j
public class SkinJsonParserUtil {

    public static final String JSON_FILE_PATH = "static/data.json";

    /**
     * 解析JSON文件
     */
    public List<JsonSkinDataDTO> parseJsonFile(String filePath) {
        try {
            String jsonStr = FileUtil.readUtf8String(filePath);
            return JSONUtil.toList(jsonStr, JsonSkinDataDTO.class);
        } catch (Exception e) {
            log.error("解析JSON文件失败: {}", filePath, e);
            return Lists.newArrayList();
        }
    }

    /**
     * 解析JSON字符串
     */
    public List<JsonSkinDataDTO> parseJsonString(String jsonStr) {
        try {
            return JSONUtil.toList(jsonStr, JsonSkinDataDTO.class);
        } catch (Exception e) {
            log.error("解析JSON字符串失败", e);
            return Lists.newArrayList();
        }
    }

    /**
     * 将JSON数据转换为SkinItem对象
     */
    public SkinItemEntity convertToSkinItem(JsonSkinDataDTO jsonData) {
        if (jsonData == null) {
            return null;
        }

        SkinItemEntity skinItem = new SkinItemEntity();
        skinItem.setSkinItemId(Long.parseLong(jsonData.getItemId()));
        skinItem.setSkinMarketHashName(jsonData.getMarket_hash_name());
        skinItem.setSkinName(jsonData.getName());
        skinItem.setSkinImageUrl(jsonData.getImage_url());

        // 从市场哈希名称中解析更多信息
        parseAdditionalInfo(skinItem, jsonData.getMarket_hash_name());

        return skinItem;
    }

    /**
     * 从市场哈希名称中解析武器类型、磨损等级等信息
     */
    private void parseAdditionalInfo(SkinItemEntity skinItem, String marketHashName) {
        if (StrUtil.isBlank(marketHashName)) {
            return;
        }

        // 解析武器类型
        String weapon = extractWeapon(marketHashName);
        if (StrUtil.isNotBlank(weapon)) {
            skinItem.setSkinWeapon(weapon);
        }

        // 解析磨损等级
        String exterior = extractExterior(marketHashName);
        if (StrUtil.isNotBlank(exterior)) {
            skinItem.setSkinExterior(exterior);
        }

        // 解析是否StatTrak
        int isStatTrak = (marketHashName.contains("StatTrak™") ||
                marketHashName.contains("StatTrak")) ? 1 : 0;
        skinItem.setIsStattrak(isStatTrak);

        // 解析是否纪念品
        int isSouvenir = marketHashName.contains("Souvenir") ? 1 : 0;
        skinItem.setIsSouvenir(isSouvenir);

        // 解析稀有度（简化的逻辑，实际需要更复杂的解析）
        String rarity = extractRarity(marketHashName);
        if (StrUtil.isNotBlank(rarity)) {
            skinItem.setSkinRarity(rarity);
        }
    }

    private String extractWeapon(String marketHashName) {
        // 常见的武器类型列表
        String[] weapons = {
                "AK-47", "M4A4", "M4A1-S", "AWP", "Desert Eagle", "USP-S", "Glock-18",
                "P250", "Tec-9", "Five-SeveN", "CZ75-Auto", "P90", "MP9", "MP7",
                "UMP-45", "MAC-10", "Galil AR", "FAMAS", "SG 553", "AUG",
                "SSG 08", "SCAR-20", "G3SG1", "MAG-7", "Nova", "XM1014", "Sawed-Off",
                "M249", "Negev", "R8 Revolver", "Bayonet", "Karambit", "M9 Bayonet",
                "Butterfly Knife", "Flip Knife", "Gut Knife", "Huntsman Knife",
                "Falchion Knife", "Shadow Daggers", "Bowie Knife", "Navaja Knife",
                "Stiletto Knife", "Talon Knife", "Ursus Knife", "Nomad Knife",
                "Skeleton Knife", "Survival Knife", "Paracord Knife",
                "Sport Gloves", "Driver Gloves", "Hand Wraps", "Moto Gloves",
                "Specialist Gloves", "Bloodhound Gloves", "Hydra Gloves"
        };

        for (String weapon : weapons) {
            if (marketHashName.contains(weapon)) {
                return weapon;
            }
        }

        // 如果没找到具体武器，尝试判断类型
        if (marketHashName.contains("Music Kit")) {
            return "Music Kit";
        } else if (marketHashName.contains("Sticker")) {
            return "Sticker";
        } else if (marketHashName.contains("Gloves")) {
            return "Gloves";
        } else if (marketHashName.contains("Knife")) {
            return "Knife";
        }

        return "Unknown";
    }

    private String extractExterior(String marketHashName) {
        // 磨损等级列表
        String[] exteriors = {
                "Factory New", "Minimal Wear", "Field-Tested",
                "Well-Worn", "Battle-Scarred"
        };

        for (String exterior : exteriors) {
            if (marketHashName.contains(exterior)) {
                return exterior;
            }
        }

        return "Unknown";
    }

    private String extractRarity(String marketHashName) {
        // 稀有度关键词
        if (marketHashName.contains("Contraband")) {
            return "Contraband";
        } else if (marketHashName.contains("Covert") || marketHashName.contains("★")) {
            return "Covert";
        } else if (marketHashName.contains("Classified")) {
            return "Classified";
        } else if (marketHashName.contains("Restricted")) {
            return "Restricted";
        } else if (marketHashName.contains("Mil-Spec")) {
            return "Mil-Spec";
        } else if (marketHashName.contains("Industrial")) {
            return "Industrial";
        } else if (marketHashName.contains("Consumer")) {
            return "Consumer";
        }

        return "Unknown";
    }
}
