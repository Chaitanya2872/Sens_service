package com.bmsedge.iotsensor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import lombok.extern.slf4j.Slf4j;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication
@Slf4j
public class IotSensorApplication {

    /**
     * ✅ Set IST timezone on application startup
     * This ensures all timestamps are saved in Indian Standard Time
     */
    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        log.info("========================================");
        log.info("✅ Application timezone set to: {}", TimeZone.getDefault().getID());
        log.info("✅ Current IST time: {}", java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        log.info("========================================");
    }

    public static void main(String[] args) {
        SpringApplication.run(IotSensorApplication.class, args);
    }
}