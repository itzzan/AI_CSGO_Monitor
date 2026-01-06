package com.zan.csgo.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * @Author Zan
 * @Create 2026/1/5 17:41
 * @ClassName: MetaObjectHandlerConfig
 * @Description : 自动填充配置
 */
@Component
public class MetaObjectHandlerConfig implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "version", Integer.class, 0);
        this.strictInsertFill(metaObject, "delFlag", Boolean.class, false);
        this.strictInsertFill(metaObject, "createdAt", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "changedAt", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "changedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
