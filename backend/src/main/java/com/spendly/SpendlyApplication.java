package com.spendly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
// RefreshTokenService purges expired rows on a schedule.
@EnableScheduling
public class SpendlyApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpendlyApplication.class, args);
    }
}
