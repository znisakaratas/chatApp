package com.example.chat.dto;
import java.util.List;

public record UpdateGroup(String name, List<Long> userIds) {}
