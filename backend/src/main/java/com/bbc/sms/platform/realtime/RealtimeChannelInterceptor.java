package com.bbc.sms.platform.realtime;

import com.bbc.sms.platform.security.AppUserPrincipal;
import com.bbc.sms.platform.security.ParcoursAccessService;
import com.bbc.sms.platform.security.SectionRoles;
import com.bbc.sms.platform.security.SessionTokenService;
import com.bbc.sms.platform.tenant.ParcoursContext;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.settings.AccessControlService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/** Realtime is an invalidation signal only; sensitive records are read through scoped REST APIs. */
@Component
public class RealtimeChannelInterceptor implements ChannelInterceptor {
    private static final String TOKEN = RealtimeChannelInterceptor.class.getName() + ".token";
    private static final String SCOPE = RealtimeChannelInterceptor.class.getName() + ".scope";
    private static final Map<String, String> ACTIONS = Map.of(
            "attendance", "ATTENDANCE_ROSTER_VIEW", "payments", "PAYMENT_VIEW", "events", "EVENTS_VIEW");
    private final SessionTokenService sessions;
    private final AccessControlService access;
    private final ParcoursAccessService parcours;

    public RealtimeChannelInterceptor(SessionTokenService sessions, AccessControlService access,
                                      ParcoursAccessService parcours) {
        this.sessions = sessions;
        this.access = access;
        this.parcours = parcours;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null || headers.getCommand() == null) return message; // heartbeat
        StompCommand command = headers.getCommand();
        if (command == StompCommand.DISCONNECT || command == StompCommand.UNSUBSCRIBE) return message;
        Map<String, Object> attributes = headers.getSessionAttributes();
        if (attributes == null) throw denied();
        if (command == StompCommand.CONNECT) {
            String authorization = headers.getFirstNativeHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) throw denied();
            String token = authorization.substring(7);
            AppUserPrincipal principal = sessions.requireAccess(token);
            attributes.put(TOKEN, token);
            String scope = headers.getFirstNativeHeader("X-Parcours");
            if (scope != null) attributes.put(SCOPE, scope);
            headers.setUser(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            return message;
        }
        // There are no client-writable STOMP destinations. Prevent forged broker broadcasts.
        if (command != StompCommand.SUBSCRIBE || !(attributes.get(TOKEN) instanceof String token)) throw denied();
        AppUserPrincipal principal = sessions.requireAccess(token);
        String destination = headers.getDestination();
        String prefix = "/topic/school/" + principal.schoolId() + "/";
        if (destination == null || !destination.startsWith(prefix)) throw denied();
        String action = ACTIONS.get(destination.substring(prefix.length()));
        if (action == null) throw denied(); // also rejects wildcards, nested paths and private queues

        var previousSecurity = SecurityContextHolder.getContext();
        var previousTenant = TenantContext.isSet() ? TenantContext.get() : null;
        var previousScope = ParcoursContext.get();
        String previousSection = ParcoursContext.sectionLock();
        try {
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            SecurityContextHolder.setContext(context);
            TenantContext.set(principal.schoolId());
            ParcoursContext.set(ParcoursContext.parse((String) attributes.get(SCOPE)));
            ParcoursContext.lockSection(SectionRoles.sectionOf(principal.roleCode()));
            if ("EXPLICIT".equals(parcours.scopeMode(principal.userId()))
                    && (ParcoursContext.get() == null || !parcours.isAllowed(principal.userId(), ParcoursContext.get()))) {
                throw denied();
            }
            boolean permitted = access.capabilities().actions().stream().anyMatch(capability ->
                    action.equals(capability.actionCode()) && Set.of("ALLOW", "CONTEXT_REQUIRED").contains(capability.effect()));
            if (!permitted) throw denied();
            return message;
        } finally {
            SecurityContextHolder.setContext(previousSecurity);
            if (previousTenant == null) TenantContext.clear(); else TenantContext.set(previousTenant);
            ParcoursContext.clear();
            ParcoursContext.set(previousScope);
            ParcoursContext.lockSection(previousSection);
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Realtime destination not authorized");
    }
}
