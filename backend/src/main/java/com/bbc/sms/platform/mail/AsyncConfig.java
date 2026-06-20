package com.bbc.sms.platform.mail;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables @Async so e-mail notifications never block the request thread. */
@Configuration
@EnableAsync
public class AsyncConfig {
}
