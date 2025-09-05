package com.example.chat;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.*;

public final class JwtUtil {


    public static String extractMainRole(Jwt jwt) {
        List<String> realm = Optional.ofNullable(jwt.<Map<String,Object>>getClaim("realm_access"))
                .map(m -> (Collection<?>) m.get("roles"))
                .map(c -> c.stream().map(String::valueOf).toList())
                .orElse(List.of());

        String azp = jwt.getClaimAsString("azp");
        List<String> client = Optional.ofNullable(jwt.<Map<String,Object>>getClaim("resource_access"))
                .map(ra -> (Map<String,Object>) ra.get(azp))
                .map(cm -> (Collection<?>) cm.get("roles"))
                .map(c -> c.stream().map(String::valueOf).toList())
                .orElse(List.of());

        if (realm.contains("admin") || client.contains("client_admin")) return "admin";
        if (realm.contains("user")) return "user";
        if (!realm.isEmpty()) return realm.get(0);
        if (!client.isEmpty()) return client.get(0);
        return null; // istersen "user" defaultu ver
    }
}
