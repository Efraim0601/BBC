package com.bbc.sms.documents;

import com.bbc.sms.platform.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class LocalDocumentStorage implements DocumentStorage {
    private final Path root;
    public LocalDocumentStorage(@Value("${bbc.documents.storage-path}") String root) {
        this.root = Path.of(root).toAbsolutePath().normalize();
    }
    @Override public String store(String schoolId, String documentId, String extension, byte[] content) {
        try {
            String safeExtension = extension == null || extension.isBlank() ? "bin" : extension.replaceAll("[^A-Za-z0-9]", "");
            Path file = root.resolve(schoolId).resolve(documentId + "." + safeExtension).normalize();
            if (!file.startsWith(root)) throw new SecurityException("Invalid storage key");
            Files.createDirectories(file.getParent());
            Files.write(file, content);
            return root.relativize(file).toString().replace('\\', '/');
        } catch (IOException ex) { throw new IllegalStateException("Impossible de stocker le document", ex); }
    }
    @Override public byte[] read(String key) {
        try {
            Path file = root.resolve(key).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) throw ApiException.notFound("Fichier document");
            return Files.readAllBytes(file);
        } catch (IOException ex) { throw ApiException.notFound("Fichier document"); }
    }
}
