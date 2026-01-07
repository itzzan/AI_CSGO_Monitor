package com.zan.csgo.service;

/**
 * @Author Zan
 * @Create 2026/1/6 10:07
 * @ClassName: IDataService
 * @Description : 数据服务接口
 */
public interface IDataService {

    /**
     * 从JSON文件导入饰品数据
     *
     * @param filePath JSON文件路径
     */
    void importSkinData(String filePath);

    /**
     * 同步本地字典数据
     *
     * @param filePath JSON文件路径
     */
    void importSkinPlatformData(String filePath);
}
