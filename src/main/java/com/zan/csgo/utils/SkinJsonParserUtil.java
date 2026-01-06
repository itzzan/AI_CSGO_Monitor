package com.zan.csgo.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.google.common.collect.Lists;
import com.zan.csgo.model.dto.JsonSkinDataDTO;
import com.zan.csgo.model.entity.SkinItemEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    // 预编译正则：匹配标准磨损等级 (严格匹配括号结尾)
    private static final Pattern EXTERIOR_PATTERN = Pattern.compile("\\((Factory New|Minimal Wear|Field-Tested|Well-Worn|Battle-Scarred)\\)$");

    // 预编译正则：匹配前缀 (StatTrak 或 Souvenir)
    private static final Pattern PREFIX_PATTERN = Pattern.compile("^(StatTrak™|Souvenir)\\s+");

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

        // 1. 中文名 (对应 skin_name)
        skinItem.setSkinName(jsonData.getName());
        skinItem.setSkinImageUrl(jsonData.getImage_url());

        // 2. 核心解析：从 Hash Name 拆解出 Type, Pattern, Category, Rarity 等
        parseAdditionalInfo(skinItem, jsonData.getMarket_hash_name());

        return skinItem;
    }

    /**
     * 核心解析逻辑
     */
    private void parseAdditionalInfo(SkinItemEntity skinItem, String marketHashName) {
        if (StrUtil.isBlank(marketHashName)) {
            return;
        }

        String tempName = marketHashName.trim();

        // --- Step 1: 处理星标 (★) ---
        if (tempName.startsWith("★")) {
            skinItem.setIsStar(1);
            tempName = tempName.replace("★", "").trim();
        } else {
            skinItem.setIsStar(0);
        }

        // --- Step 2: 处理前缀 (StatTrak / Souvenir) ---
        Matcher prefixMatcher = PREFIX_PATTERN.matcher(tempName);
        if (prefixMatcher.find()) {
            String prefix = prefixMatcher.group(1);
            if ("StatTrak™".equals(prefix)) skinItem.setIsStattrak(1);
            if ("Souvenir".equals(prefix)) skinItem.setIsSouvenir(1);
            // 移除前缀
            tempName = prefixMatcher.replaceFirst("").trim();
        }

        // --- Step 3: 提取磨损 (Exterior) ---
        Matcher extMatcher = EXTERIOR_PATTERN.matcher(tempName);
        if (extMatcher.find()) {
            skinItem.setSkinExterior(extMatcher.group(1));
            // 移除磨损部分
            tempName = extMatcher.replaceFirst("").trim();
        }

        // --- Step 4: 分割 Weapon 和 Pattern ---
        // 此时 tempName 应该只剩下 "AK-47 | Redline" 或 "Butterfly Knife"
        // 使用 " | " 进行分割
        String[] parts = tempName.split("\\s\\|\\s");

        if (parts.length >= 1) {
            // 第一部分是武器/类型 (如 AK-47, Sticker, Charm)
            String weapon = parts[0];
            skinItem.setSkinWeapon(weapon);

            // 根据 Weapon 智能判断大类 (Category)
            skinItem.setSkinCategory(determineCategory(weapon));

            // 第二部分及以后是 皮肤/图案名 (Pattern)
            if (parts.length >= 2) {
                // 处理多段式命名 (如: Souvenir Charm | Austin 2025 | Exploit)
                // 将后面所有部分拼接起来作为 Pattern
                String pattern = String.join(" | ", Arrays.copyOfRange(parts, 1, parts.length));
                skinItem.setSkinPattern(pattern);
            } else {
                // 如果没有 "|"，说明是原版 (如 ★ Butterfly Knife)
                skinItem.setSkinPattern("Vanilla");
            }
        }
    }

    /**
     * 根据具体类型归纳大类 (Category Group)
     */
    private String determineCategory(String weapon) {
        String w = weapon.toLowerCase();

        // 刀具类
        if (w.contains("knife") || w.contains("bayonet") || w.contains("karambit") || w.contains("daggers")) {
            return "Knife";
        }
        // 手套类
        if (w.contains("gloves") || w.contains("wraps")) {
            return "Gloves";
        }
        // 杂项
        if (w.contains("sticker")) return "Sticker";
        if (w.contains("music kit")) return "Music Kit";
        if (w.contains("charm")) return "Charm"; // 挂件
        if (w.contains("agent")) return "Agent"; // 探员
        if (w.contains("case") || w.contains("capsule") || w.contains("package")) return "Container";
        if (w.contains("key")) return "Tool";
        if (w.contains("patch")) return "Patch"; // 布章
        if (w.contains("graffiti")) return "Graffiti"; // 涂鸦
        if (w.contains("pin")) return "Pin"; // 徽章

        // 枪械细分
        if (StrUtil.equalsAnyIgnoreCase(weapon,
                "AK-47", "M4A4", "M4A1-S", "AWP", "Galil AR", "FAMAS", "AUG", "SG 553", "SSG 08", "SCAR-20", "G3SG1")) {
            return "Rifle";
        }
        if (StrUtil.equalsAnyIgnoreCase(weapon,
                "Glock-18", "USP-S", "P2000", "P250", "Desert Eagle", "Five-SeveN", "Tec-9", "CZ75-Auto", "Dual Berettas", "R8 Revolver")) {
            return "Pistol";
        }
        if (StrUtil.equalsAnyIgnoreCase(weapon,
                "MAC-10", "MP9", "MP7", "UMP-45", "P90", "PP-Bizon", "MP5-SD")) {
            return "SMG";
        }
        if (StrUtil.equalsAnyIgnoreCase(weapon,
                "Nova", "XM1014", "MAG-7", "Sawed-Off", "M249", "Negev")) {
            return "Heavy"; // 散弹枪和机枪统称为重武器
        }

        return "Other";
    }
}
