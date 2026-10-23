package com.insurance.renewal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RenewalBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(RenewalBackendApplication.class, args);
    }
}
