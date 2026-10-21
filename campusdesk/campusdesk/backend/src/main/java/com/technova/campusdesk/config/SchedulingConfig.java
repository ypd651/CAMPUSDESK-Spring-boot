package com.technova.campusdesk.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled (auto-close job) and @Async (notification listener). */
@Configuration
@EnableScheduling
@EnableAsync
public class SchedulingConfig {
}
