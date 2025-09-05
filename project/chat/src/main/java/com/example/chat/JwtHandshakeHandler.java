// JwtHandshakeHandler.java
package com.example.chat;

import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.util.MultiValueMap;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;

final class JwtHandshakeHandler extends DefaultHandshakeHandler {
    private final JwtDecoder jwtDecoder;
    private final AppUserRepository userRepo;

    JwtHandshakeHandler(JwtDecoder jwtDecoder, AppUserRepository userRepo) {
        this.jwtDecoder = jwtDecoder;
        this.userRepo = userRepo;
    }

    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {

        // 1) Token'ı al (query: access_token veya Authorization: Bearer ...)
        String token = getAccessToken(request);
        if (token == null) return null; // reddedilir

        // 2) Decode et
        Jwt jwt = jwtDecoder.decode(token);
        String sub = jwt.getSubject();

        // 3) DB'den AppUser bul (sub = keycloakId)
        String nameForPrincipal = Optional.ofNullable(
                userRepo.findByKeycloakId(sub).map(u -> String.valueOf(u.getId())).orElse(null)
        ).orElse(sub); // bulunamazsa sub'a düş

        // 4) Principal (authority YOK)
        return () -> nameForPrincipal; // Principal::getName
    }

    private String getAccessToken(ServerHttpRequest request) {
        MultiValueMap<String, String> q = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams();

        // Önce jwtToken'a bak, yoksa access_token'a düş
        String qTok = q.getFirst("jwtToken");
        if (qTok == null || qTok.isBlank()) {
            qTok = q.getFirst("access_token");
        }
        if (qTok != null && !qTok.isBlank()) return qTok;

        // Authorization: Bearer ...
        String h = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (h != null && h.startsWith("Bearer ")) return h.substring(7);

        return null;
    }
    }
