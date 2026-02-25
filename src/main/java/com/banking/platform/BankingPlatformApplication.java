package com.banking.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableCaching
@EnableScheduling
@EnableMongoAuditing
public class BankingPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankingPlatformApplication.class, args);
    }
}
