package com.uai.buslines;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * BH Bus Lines — Neighborhood Bus Explorer.
 *
 * <p>Service entry point. Hexagonal architecture; base package {@code com.uai.buslines}
 * (uAI RULE-JAVA-01). Single Maven module, internal port 8085.
 *
 * <p>See {@code CLAUDE.md} for architectural deviations (no {@code tenant_id}, no PostGIS).
 *
 * <p>{@code @EnableScheduling} activates the {@code @Scheduled} import trigger
 * (task_05). The scheduler bean is guarded by
 * {@code buslines.import.scheduler.enabled} so it can be disabled in tests.
 */
@SpringBootApplication
@EnableScheduling
public class BuslinesApplication {

    public static void main(String[] args) {
        SpringApplication.run(BuslinesApplication.class, args);
    }
}
