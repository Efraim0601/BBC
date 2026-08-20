package com.bbc.sms.documents;

public interface DocumentStorage {
    default String store(String schoolId, String documentId, byte[] content) {
        return store(schoolId, documentId, "pdf", content);
    }
    String store(String schoolId, String documentId, String extension, byte[] content);
    byte[] read(String key);
}
