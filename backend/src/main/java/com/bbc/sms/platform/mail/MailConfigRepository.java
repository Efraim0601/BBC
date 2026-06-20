package com.bbc.sms.platform.mail;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MailConfigRepository extends JpaRepository<MailConfig, UUID> {
}
