package com.example.chat.dto;
public record GrantView(Long userId, boolean canUpdate, boolean canDelete) {}