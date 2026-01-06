package com.zan.csgo.controller;

import com.zan.csgo.model.common.Result;
import com.zan.csgo.service.IDataService;
import com.zan.csgo.utils.SkinJsonParserUtil;
import jakarta.annotation.Resource;
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

    @RequestMapping("/import")
    public Result<Void> importData() {
        dataService.importFromJsonFile(SkinJsonParserUtil.JSON_FILE_PATH);
        return Result.success();
    }
}
