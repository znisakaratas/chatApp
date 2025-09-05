package com.example.chat;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface GroupPermissionRepository extends JpaRepository<GroupPermission, GroupPermissionId> {

    boolean existsByGroupIdAndUserId(Long groupId, Long userId);

    Optional<GroupPermission> findByGroupIdAndUserId(Long groupId, Long userId);

    void deleteByGroupIdAndUserId(Long groupId, Long userId);
    List<GroupPermission> findAllByGroupId(Long groupId); // listGrants için
    void deleteAllByGroupIdAndUserIdNotIn(Long groupId, Set<Long> userIds);

}
