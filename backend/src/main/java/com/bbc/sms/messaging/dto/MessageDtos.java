package com.bbc.sms.messaging.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public class MessageDtos {

    /** A correspondence notice as shown in the UI, with the read-receipt state flattened. */
    public record NoticeView(
            UUID id,
            UUID studentId,
            String studentName,
            String className,
            String category,
            String subject,
            String body,
            boolean requiresAck,
            boolean acknowledged,
            Instant acknowledgedAt,
            String acknowledgedBy,
            String senderName,
            Instant createdAt) {}

    /**
     * Create a notice. {@code category} is a free string but expected to be one of:
     * info | convocation | absence | reminder | congrats.
     */
    public record NoticeUpsert(
            @NotNull UUID studentId,
            @NotBlank String category,
            @NotBlank String subject,
            @NotBlank String body,
            boolean requiresAck) {}

    /** Parent signature / read receipt: the name of the person acknowledging the notice. */
    public record AckRequest(@NotBlank String signedBy) {}
}
