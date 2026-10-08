package com.technova.campusdesk.config;

import com.technova.campusdesk.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class, BootstrapProperties.class})
public class AppConfig {
}
