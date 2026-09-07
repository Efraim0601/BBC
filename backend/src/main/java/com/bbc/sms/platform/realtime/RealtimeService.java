package com.bbc.sms.platform.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.Map;

/**
 * Pushes domain events to connected clients over STOMP.
 * Topics are tenant-scoped: /topic/school/{schoolId}/{channel}.
 */
@Service
public class RealtimeService {

    private final SimpMessagingTemplate messaging;

    public RealtimeService(SimpMessagingTemplate messaging) { this.messaging = messaging; }

    /** Never put student, attendance, payment or event records on a school-wide topic. */
    public void broadcast(UUID schoolId, String channel, Object payload) {
        messaging.convertAndSend("/topic/school/" + schoolId + "/" + channel, Map.of("changed", true));
    }

    /** Send a private message to a single user (e.g. parent notification). */
    public void toUser(String username, String channel, Object payload) {
        messaging.convertAndSendToUser(username, "/queue/" + channel, payload);
    }
}
