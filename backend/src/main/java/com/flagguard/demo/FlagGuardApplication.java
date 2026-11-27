package com.flagguard.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FlagGuardApplication {
    public static void main(String[] args) {
        SpringApplication.run(FlagGuardApplication.class, args);
    }
}