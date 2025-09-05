package com.example.chat;

import java.io.Serializable;
import java.util.Objects;

public class GroupPermissionId implements Serializable {
    private Long groupId;
    private Long userId;

    public GroupPermissionId() {}
    public GroupPermissionId(Long groupId, Long userId) {
        this.groupId = groupId;
        this.userId = userId;
    }
    // equals & hashCode
    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroupPermissionId that)) return false;
        return Objects.equals(groupId, that.groupId) && Objects.equals(userId, that.userId);
    }
    @Override public int hashCode() { return Objects.hash(groupId, userId); }

    // getters/setters
    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}
