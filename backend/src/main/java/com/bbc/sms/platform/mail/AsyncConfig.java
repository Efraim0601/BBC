package com.bbc.sms.platform.mail;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.Executor;

/** Enables @Async so e-mail notifications never block the request thread. */
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "academicBatchExecutor")
    public Executor academicBatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("academic-batch-");
        executor.initialize();
        return executor;
    }
}
