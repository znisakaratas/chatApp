// CreateGroupRequest.java
package com.example.chat.dto;
import java.util.List;

public record CreateGroup(String name, List<Long> userIds) {}
