package com.bbc.sms.platform.storage;

import com.bbc.sms.platform.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Panne du stockage objet — MinIO éteint, bucket absent, disque plein.
 *
 * <p>C'est un 503 et non un 500 : rien n'est cassé dans l'application, un
 * service dont elle dépend ne répond pas. L'écran peut inviter à réessayer.
 */
public class StorageException extends ApiException {

    public StorageException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
        initCause(cause);
    }
}
