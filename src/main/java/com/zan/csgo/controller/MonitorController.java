package com.zan.csgo.controller;

import com.zan.csgo.model.common.Result;
import com.zan.csgo.service.ISkinMonitorService;
import com.zan.csgo.vo.SkinMonitorVO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author Zan
 * @Create 2026/1/6 18:00
 * @ClassName: MonitorController
 * @Description : 监控控制器
 */
@RestController
@RequestMapping("/monitor")
public class MonitorController {

    @Resource
    private ISkinMonitorService skinMonitorService;

    /**
     * 手动刷新单个饰品价格
     * 前端点击 "刷新" 按钮时调用
     */
    @PostMapping("/refresh/{skinId}")
    public Result<SkinMonitorVO> refreshSkinPrice(@PathVariable Long skinId) {
        try {
            // 1. 调用 Service 获取业务 VO
            SkinMonitorVO vo = skinMonitorService.monitorSkin(skinId);

            // 2. 返回对应的 Result
            return Result.success(vo);
        } catch (Exception e) {
            // 系统级异常
            return Result.failed("系统异常: " + e.getMessage());
        }
    }
}
