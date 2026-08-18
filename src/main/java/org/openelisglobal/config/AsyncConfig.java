package org.openelisglobal.config;

import jakarta.annotation.Nullable;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurerSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Application-wide {@code @Async} executor.
 *
 * <p>
 * Previously a {@link org.springframework.core.task.SimpleAsyncTaskExecutor},
 * which spawns one unpooled thread per task with no upper bound. That is
 * dangerous for the panel sync listeners: a single trigger fans out two
 * integration calls per panel (Odoo JSON-RPC and CIS HTTP), so a
 * resync-all-panels on a large catalog could spawn hundreds of concurrent
 * threads and overwhelm Odoo.
 *
 * <pre>
 *   submit ──▶ [core 4 threads] ──full──▶ [queue 512] ──full──▶ [grow to max 16]
 *                                                                    │
 *                                                                 saturated
 *                                                                    ▼
 *                                                        CallerRunsPolicy:
 *                                                        submitting thread
 *                                                        runs the task itself
 * </pre>
 *
 * <p>
 * {@link ThreadPoolExecutor.CallerRunsPolicy} is deliberate. The alternative
 * default, {@code AbortPolicy}, throws {@code RejectedExecutionException} on
 * the <em>submitting</em> thread, where {@link AsyncExceptionHandler} cannot
 * see it (that handler only covers exceptions thrown inside an {@code @Async}
 * method body), so saturation would surface as a silent dropped sync.
 * CallerRuns applies backpressure instead: the work always happens, the
 * submitter just slows down. Safe for the panel listeners because they run
 * {@code AFTER_COMMIT}, so no database transaction is open while the caller
 * absorbs the task.
 */
@Configuration
@EnableAsync
public class AsyncConfig extends AsyncConfigurerSupport {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(512);
        executor.setThreadNamePrefix("oe-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // Do not drop in-flight integration syncs when the container stops.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Override
    @Nullable
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new AsyncExceptionHandler();
    }
}
