package com.shop.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Enables {@code @Async} and provides a <b>bounded</b> executor for domain-event handling.
 *
 * <p>We do NOT rely on the default {@code SimpleAsyncTaskExecutor} (which spawns a new thread per
 * task, unbounded) — that is a denial-of-service risk under load. This pool has a fixed ceiling
 * and a bounded queue; when both are saturated, {@code CallerRunsPolicy} applies natural
 * back-pressure by running the task on the caller thread instead of dropping it.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /** Executor used by {@code @Async("orderEventExecutor")} listeners. */
    @Bean(name = "orderEventExecutor")
    public Executor orderEventExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("order-event-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // Carry the caller's MDC (correlation id) into the async thread so async handler logs stay
        // traceable to the originating request, then restore the worker's previous context.
        executor.setTaskDecorator(mdcPropagatingDecorator());
        executor.initialize();
        log.info("Initialized orderEventExecutor: core=2 max=4 queue=100 (bounded, CallerRunsPolicy, MDC-propagating)");
        return executor;
    }

    /** Copies the submitting thread's MDC onto the worker thread for the duration of the task. */
    private static TaskDecorator mdcPropagatingDecorator() {
        return runnable -> {
            Map<String, String> submitterContext = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previousContext = MDC.getCopyOfContextMap();
                if (submitterContext != null) {
                    MDC.setContextMap(submitterContext);
                } else {
                    MDC.clear();
                }
                try {
                    runnable.run();
                } finally {
                    if (previousContext != null) {
                        MDC.setContextMap(previousContext);
                    } else {
                        MDC.clear();
                    }
                }
            };
        };
    }
}
