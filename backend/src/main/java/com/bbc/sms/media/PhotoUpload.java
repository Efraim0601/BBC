package com.bbc.sms.media;

import jakarta.validation.constraints.NotBlank;

/**
 * Corps d'un envoi de photo : l'image en data URL, produite par la capture
 * navigateur (selfie ou fichier importé) après recadrage et compression.
 */
public record PhotoUpload(@NotBlank String dataUrl) {}
