package com.example.chat.dto;
import java.util.List;

public record GroupResponse(Long id, String name, List<Long> memberIds, boolean adminOwned) {}
