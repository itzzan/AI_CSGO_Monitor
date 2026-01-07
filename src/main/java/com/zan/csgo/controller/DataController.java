package com.zan.csgo.controller;

import com.zan.csgo.model.common.Result;
import com.zan.csgo.service.IDataService;
import com.zan.csgo.utils.SkinJsonParserUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author Zan
 * @Create 2026/1/6 10:08
 * @ClassName: DataController
 * @Description : 数据控制器
 */
@RestController
@RequestMapping("/data")
public class DataController {

    @Resource
    private IDataService dataService;

    @PostMapping("/importSkinData")
    public Result<Void> importSkinData() {
        dataService.importSkinData(SkinJsonParserUtil.JSON_SKIN_FILE_PATH);
        return Result.success();
    }

    @PostMapping("/importSkinPlatformData")
    public Result<Void> importSkinPlatformData() {
        dataService.importSkinPlatformData(SkinJsonParserUtil.JSON_SKIN_PLATFORM_FILE_PATH);
        return Result.success();
    }
}
