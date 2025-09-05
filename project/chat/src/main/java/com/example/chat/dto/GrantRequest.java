package com.example.chat.dto;
public record GrantRequest(Long userId, Boolean canUpdate, Boolean canDelete) {}
