package com.example.chat.dto;

public record MeDto(
        Long id,
        String username,
        String email,
        String firstName,
        String lastName,
        String role,
        String status
) { }
