package com.zan.csgo.core.controller;

import com.zan.common.Result;
import com.zan.csgo.core.service.ISkinInfoService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author Zan
 * @Create 2026/1/5 14:23
 * @ClassName: CsgoController
 * @Description : CSGO控制器
 */
@RestController
@RequestMapping("/csgo")
public class CsgoController {

    @Resource
    private ISkinInfoService skinInfoService;

    @PostMapping("/loadSkinInfoData")
    public Result<Boolean> loadSkinInfoData(@RequestParam("jsonFilePath") String jsonFilePath) {
        boolean result = skinInfoService.loadSkinInfoData(jsonFilePath);
        return Result.success(result);
    }
}
