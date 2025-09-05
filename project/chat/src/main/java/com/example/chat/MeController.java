package com.example.chat;

import com.example.chat.dto.MeDto;
import com.example.chat.dto.UpdateStatus;
import jakarta.transaction.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@RestController
@RequestMapping("/api")
public class MeController {

    private final AppUserRepository repo;
    private final UserProvisioningService provisioning; // <-- EKLENDİ

    public MeController(AppUserRepository repo, UserProvisioningService provisioning) {
        this.repo = repo;
        this.provisioning = provisioning;
    }

    @DeleteMapping("/{keycloakId:[0-9a-fA-F-]{36}}")
    @Transactional
    public ResponseEntity<Void> deleteByKeycloakId(@PathVariable String keycloakId) {
        provisioning.deleteByKeycloakId(keycloakId); // servis üzerinden sil
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/user")
    public MeDto me(@AuthenticationPrincipal Jwt jwt) {
        // 🔹 Burada DB'ye upsert yapılır (ilk login'de create, sonra update)
        provisioning.upsertFromJwt(jwt);

        String sub = jwt.getSubject();
        String role = JwtUtil.extractMainRole(jwt);

        return repo.findByKeycloakId(sub)
                .map(u -> new MeDto(
                        u.getId(),
                        u.getUsername(),
                        u.getEmail(),
                        u.getFirstName(),
                        u.getLastName(),
                        role,
                        u.getStatus()
                        ))
                .orElseGet(() -> new MeDto(
                        null,
                        claim(jwt, "preferred_username"),
                        claim(jwt, "email"),
                        claim(jwt, "given_name"),
                        claim(jwt, "family_name"),
                        role,
                        claim(jwt, "status")
                        ));
    }

    private static String claim(Jwt jwt, String key) {
        Object v = jwt.getClaims().get(key);
        return v == null ? null : String.valueOf(v);
    }
    @PostMapping("/user")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AppUser updateStatus(@AuthenticationPrincipal Jwt jwt,@RequestBody UpdateStatus req) {
        var user = repo.findById(req.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + req.userId()));
        String kcSub = jwt.getSubject();
        AppUser me = repo.findByKeycloakId(kcSub)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Me not found"));

        if (me.getId() != null && me.getId().equals(req.userId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot change your own status");
        }

        String requested = (req.status() == null ? "" : req.status().trim().toUpperCase());
        if (!requested.equals("ACTIVE") && !requested.equals("INACTIVE")) {
            throw new IllegalArgumentException("Invalid status: " + req.status() + " (expected ACTIVE or INACTIVE)");
        }

        user.setStatus(requested); // String tuttuğun için direkt set
        return repo.save(user);
    }

}
