package com.bmsedge.iotsensor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration for cafeteria data integration
 */
@Configuration
@EnableScheduling  // Enable scheduling for polling service
public class CafeteriaConfig {

    /**
     * RestTemplate bean for making HTTP calls to Node.js server
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}