package com.example.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

@Service
public class UserProvisioningService {
    private final AppUserRepository repo;
    static final Logger log = LoggerFactory.getLogger(UserProvisioningService.class);

    public UserProvisioningService(AppUserRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void upsertFromJwt(Jwt jwt) {
        String sub = jwt.getClaimAsString("sub");
        if (sub == null || sub.isBlank()) throw new IllegalStateException("JWT does not contain 'sub'");

        AppUser user = repo.findByKeycloakId(sub).orElseGet(() -> {
            AppUser u = new AppUser();
            u.setKeycloakId(sub);
            u.setCreatedAt(Instant.now());
            u.setStatus("ACTIVE");
            return u;
        });

        user.setUsername(get(jwt,"preferred_username", user.getUsername()));
        user.setEmail(get(jwt,"email", user.getEmail()));
        user.setFirstName(get(jwt,"given_name", user.getFirstName()));
        user.setLastName(get(jwt,"family_name", user.getLastName()));

        // Rol (senin util’in)
        if (user.getRole() == null) {
            user.setRole(JwtUtil.extractMainRole(jwt));
        }


        user.setLastLoginAt(Instant.now());
        repo.save(user);
    }

    private String get(Jwt jwt, String key) {
        Object v = jwt.getClaims().get(key);
        return v == null ? null : String.valueOf(v);
    }
    private String get(Jwt jwt, String key, String fallback) {
        Object v = jwt.getClaims().get(key);
        return v == null ? fallback : String.valueOf(v);
    }
    @Transactional
    public void upsertFromAdminEvent(String keycloakId, Map<String,Object> rep) {
        AppUser user = repo.findByKeycloakId(keycloakId).orElseGet(() -> {
            AppUser u = new AppUser();
            u.setKeycloakId(keycloakId);
            u.setCreatedAt(Instant.now());
            return u;
        });

        // representation varsa username/email/first/last güncelle
        if (rep != null) {
            user.setUsername(str(rep.get("username"), user.getUsername()));
            user.setEmail(str(rep.get("email"), user.getEmail()));
            user.setFirstName(str(rep.get("firstName"), user.getFirstName()));
            user.setLastName(str(rep.get("lastName"), user.getLastName()));
        }

        repo.save(user);
    }

    @Transactional
    public void deleteByKeycloakId(String keycloakId) {
        int affected= repo.deleteByKeycloakId(keycloakId);
        log.info("AppUser deleteByKeycloakId={} -> affectedRows={}", keycloakId, affected);

    }
    private static String str(Object v, String fallback) {
        return v == null ? fallback : String.valueOf(v);
    }

}
