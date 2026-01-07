package com.zan.csgo.service;

import com.zan.csgo.vo.SkinMonitorVO;

/**
 * @Author Zan
 * @Create 2026/1/7 10:30
 * @ClassName: ISkinMonitorService
 * @Description : 监控饰品价格服务接口
 */
public interface ISkinMonitorService {

    /**
     * 监控饰品价格
     *
     * @param skinId
     * @return
     */
    SkinMonitorVO monitorSkin(Long skinId);
}
