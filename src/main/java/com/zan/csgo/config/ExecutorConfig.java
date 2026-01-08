package com.zan.csgo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @Author Zan
 * @Create 2026/1/7 17:00
 * @ClassName: ExecutorConfig
 * @Description : 线程池配置
 */
@Configuration
public class ExecutorConfig {

    @Bean("monitorExecutor")
    public Executor monitorExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：控制并发度。建议设小一点，防止 Steam 瞬间封 IP，必须串行执行，模拟人类操作
        executor.setCorePoolSize(3);
        // 最大线程数
        executor.setMaxPoolSize(5);
        // 队列大小，防止溢出
        executor.setQueueCapacity(2000);
        // 线程名前缀，方便查日志
        executor.setThreadNamePrefix("Skin-Monitor-");
        // 拒绝策略：如果队列满了，由调用者线程自己执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
