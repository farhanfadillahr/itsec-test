package com.itsectest.shared.config;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("auditExecutor")
    public Executor auditExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean("mailExecutor")
    public Executor mailExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
