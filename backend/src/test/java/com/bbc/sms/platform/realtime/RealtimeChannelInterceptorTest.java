package com.bbc.sms.platform.realtime;

import com.bbc.sms.platform.security.*;
import com.bbc.sms.platform.tenant.TenantContext;
import com.bbc.sms.settings.AccessControlService;
import com.bbc.sms.settings.dto.AccessControlDtos.CapabilityView;
import com.bbc.sms.settings.dto.AccessControlDtos.EffectiveActionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RealtimeChannelInterceptorTest {
    private final SessionTokenService sessions = mock(SessionTokenService.class);
    private final AccessControlService access = mock(AccessControlService.class);
    private final ParcoursAccessService parcours = mock(ParcoursAccessService.class);
    private final RealtimeChannelInterceptor interceptor = new RealtimeChannelInterceptor(sessions,access,parcours);
    private final AppUserPrincipal user = new AppUserPrincipal(UUID.randomUUID(),UUID.randomUUID(),"test","accountant","Test","T");
    private final Map<String,Object> attributes = new HashMap<>();

    @BeforeEach void setup() {
        when(sessions.requireAccess("access")).thenReturn(user);
        when(parcours.scopeMode(user.userId())).thenReturn("GLOBAL");
        when(access.capabilities()).thenReturn(new CapabilityView(1,"GLOBAL",List.of(),List.of(
                new EffectiveActionView("PAYMENT_VIEW","Paiements","Payments","CONTEXT_REQUIRED","SCHOOL_ALL","TEST",true,"LOW"))));
    }

    private Message<byte[]> frame(StompCommand command, String destination, String authorization) {
        var headers = StompHeaderAccessor.create(command);
        headers.setSessionId("test-session"); headers.setSessionAttributes(attributes);
        if(destination != null) headers.setDestination(destination);
        if(authorization != null) headers.setNativeHeader("Authorization",authorization);
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0],headers.getMessageHeaders());
    }
    private void connect() { interceptor.preSend(frame(StompCommand.CONNECT,null,"Bearer access"),null); }

    @Test void anonymousAndInvalidConnectionsAreRejected() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT,null,null),null)).isInstanceOf(AccessDeniedException.class);
        when(sessions.requireAccess("refresh")).thenThrow(new BadCredentialsException("Wrong type"));
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT,null,"Bearer refresh"),null)).isInstanceOf(BadCredentialsException.class);
    }

    @Test void authorizedOwnSchoolSubscriptionContainsNoContextLeak() {
        connect();
        assertThatCode(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE,"/topic/school/"+user.schoolId()+"/payments",null),null)).doesNotThrowAnyException();
        assertThat(TenantContext.isSet()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test void crossSchoolWildcardPrivateQueueAndUnknownDestinationsAreDenied() {
        connect();
        for(String destination : List.of("/topic/school/"+UUID.randomUUID()+"/payments", "/topic/school/*/payments", "/user/another/queue/payments", "/topic/school/"+user.schoolId()+"/unknown")) {
            assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE,destination,null),null)).isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test void missingFeaturePermissionAndClientBroadcastsAreDenied() {
        connect();
        String destination="/topic/school/"+user.schoolId()+"/attendance";
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE,destination,null),null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND,destination,null),null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test void subscriptionRechecksRevocationAfterConnection() {
        connect();
        when(sessions.requireAccess("access")).thenThrow(new BadCredentialsException("Revoked"));
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE,"/topic/school/"+user.schoolId()+"/payments",null),null)).isInstanceOf(BadCredentialsException.class);
    }

    @Test void broadcastsOnlyAnInvalidationNeverStudentOrPaymentRecords() {
        var messaging=mock(SimpMessagingTemplate.class);
        new RealtimeService(messaging).broadcast(user.schoolId(),"payments",Map.of("studentName","Private child", "amount",5000));
        verify(messaging).convertAndSend("/topic/school/"+user.schoolId()+"/payments",Map.of("changed",true));
    }
}
