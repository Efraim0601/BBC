package com.bbc.sms.documents;

public interface DocumentStorage {
    String store(String schoolId, String documentId, byte[] content);
    byte[] read(String key);
}
