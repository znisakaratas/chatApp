// WsEventsListener.java (güncel)
package com.example.chat;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.security.Principal;

@Component
public class WsEventsListener {
    private final BacklogDeliveryService backlog;

    public WsEventsListener(BacklogDeliveryService backlog) {
        this.backlog = backlog;
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent ev) {
        var sha = StompHeaderAccessor.wrap(ev.getMessage());
        var dest = sha.getDestination();
        Principal p = sha.getUser();
        if (p == null || dest == null) return;

        // Kişisel kuyruk dinleniyorsa bu kullanıcı için consumer aç
        if (dest.endsWith("/queue/messages") || dest.contains("/queue/messages")) {
            String uid = p.getName(); // JwtHandshakeHandler -> AppUser.id
            backlog.startConsumingForUser(uid);
        }
    }
    @EventListener
    public void onDisconnect(SessionDisconnectEvent ev) {
        var user = ev.getUser();
        if (user != null) backlog.stopConsumingForUser(user.getName());
    }
}
