package com.zan.csgo;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 开启定时任务
@EnableAsync // 开启异步线程池
@MapperScan("com.zan.csgo.mapper")
public class AiCsgoMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCsgoMonitorApplication.class, args);
    }

}
