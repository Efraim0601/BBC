package com.bbc.sms.classkit.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Class resources (fournitures & livres) payloads. */
public class ClassKitDtos {

    public record ItemView(UUID id, String label, Integer quantity, Long price, String note,
                           String subjectCode, String author, Boolean mandatory) {}

    public record ItemUpsert(
            @NotBlank String label,
            Integer quantity,
            Long price,
            String note,
            String subjectCode,
            String author,
            Boolean mandatory) {}

    /** A class' list for one kind, plus its publish state. */
    public record ClassResourceView(
            UUID classId,
            String className,
            String kind,
            boolean published,
            OffsetDateTime publishedAt,
            List<ItemView> items) {}

    public record PublishRequest(boolean published) {}
}
