package com.bbc.sms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Bayo Bilingual Complex — School Management System.
 * Modular monolith: each business module lives in its own package under com.bbc.sms.
 */
@SpringBootApplication
public class BbcSmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(BbcSmsApplication.class, args);
    }
}
