package com.example.chat;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "group_permission")
@IdClass(GroupPermissionId.class)
public class GroupPermission {

    // getters/setters
    @Getter
    @Setter
    @Id
    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Getter
    @Setter
    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Setter
    @Getter
    @Column(name = "can_update", nullable = false)
    private boolean canUpdate = true;

    @Setter
    @Getter
    @Column(name = "can_delete", nullable = false)
    private boolean canDelete = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", insertable = false, updatable = false)
    private AppGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private AppUser user;

    public GroupPermission() {}
    public GroupPermission(Long groupId, Long userId, boolean canUpdate, boolean canDelete) {
        this.groupId = groupId;
        this.userId = userId;
        this.canUpdate = canUpdate;
        this.canDelete = canDelete;
    }
}
