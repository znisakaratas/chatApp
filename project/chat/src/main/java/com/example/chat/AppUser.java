package com.example.chat;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Setter
@Getter
@Entity
@Table(name = "app_user")
public class AppUser {
    // --- getters/setters ---
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "keycloak_id", nullable = false, unique = true)
    private String keycloakId;
    @Column(nullable = false, unique = true)
    private String username;

    @Column(unique = true)
    private String email;

    private String firstName;
    private String lastName;

    private Instant createdAt = Instant.now();
    private Instant lastLoginAt;
    // AppUser.java
    @Column
    private String role;
    private  String status;
    @PrePersist
    public void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null || status.isBlank()) status = "ACTIVE";
    }
}