package com.bbc.sms.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/** Photo de profil d'un élève ou d'un employé — une seule par personne. */
@Entity
@Table(name = "profile_photo")
@IdClass(ProfilePhoto.Key.class)
@Getter
@Setter
public class ProfilePhoto {

    /** student | employee */
    @Id
    @Column(name = "owner_type", nullable = false)
    private String ownerType;

    @Id
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "school_id", nullable = false)
    private UUID schoolId;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "bytes", nullable = false)
    private byte[] bytes;

    @Column(name = "byte_size", nullable = false)
    private int byteSize;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    /** Clé composite (type, identifiant du propriétaire). */
    public static class Key implements Serializable {
        private String ownerType;
        private UUID ownerId;

        public Key() {}

        public Key(String ownerType, UUID ownerId) {
            this.ownerType = ownerType;
            this.ownerId = ownerId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(ownerType, k.ownerType) && Objects.equals(ownerId, k.ownerId);
        }

        @Override
        public int hashCode() { return Objects.hash(ownerType, ownerId); }
    }
}
