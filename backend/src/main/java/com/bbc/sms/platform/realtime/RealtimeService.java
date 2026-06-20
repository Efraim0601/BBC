package com.bbc.sms.platform.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Pushes domain events to connected clients over STOMP.
 * Topics are tenant-scoped: /topic/school/{schoolId}/{channel}.
 */
@Service
public class RealtimeService {

    private final SimpMessagingTemplate messaging;

    public RealtimeService(SimpMessagingTemplate messaging) { this.messaging = messaging; }

    /** Broadcast to everyone watching a channel for a given school. */
    public void broadcast(UUID schoolId, String channel, Object payload) {
        messaging.convertAndSend("/topic/school/" + schoolId + "/" + channel, payload);
    }

    /** Send a private message to a single user (e.g. parent notification). */
    public void toUser(String username, String channel, Object payload) {
        messaging.convertAndSendToUser(username, "/queue/" + channel, payload);
    }
}
