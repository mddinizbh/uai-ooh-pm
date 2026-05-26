package com.uai.buslines.adapter.in.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

/**
 * Web adapter configuration for the import trigger.
 *
 * <p>Provides the {@code importTaskExecutor} bean used by
 * {@link InternalImportController} to fire imports asynchronously.
 *
 * <p>Tests may override this bean with a {@code SyncTaskExecutor} via
 * {@code @TestConfiguration} + {@code @Primary} to make the async call
 * synchronous and assert on the result within the same thread.
 */
@Configuration
class ImportWebConfiguration {

    /**
     * Task executor for async GTFS import triggers.
     *
     * <p>Uses {@link SimpleAsyncTaskExecutor} which creates a new daemon thread
     * per submission. The single-flight guard inside
     * {@link com.uai.buslines.application.usecase.ImportNetworkUseCaseImpl} ensures
     * that at most one import runs at a time regardless of how many tasks are submitted.
     */
    @Bean("importTaskExecutor")
    public TaskExecutor importTaskExecutor() {
        return new SimpleAsyncTaskExecutor("import-");
    }
}
