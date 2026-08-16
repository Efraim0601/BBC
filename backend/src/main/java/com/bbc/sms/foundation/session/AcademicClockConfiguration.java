package com.bbc.sms.foundation.session;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AcademicClockConfiguration {
    @Bean
    public Clock academicClock() {
        return Clock.systemUTC();
    }
}
