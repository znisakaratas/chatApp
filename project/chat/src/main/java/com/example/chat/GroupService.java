// GroupService.java
package com.example.chat;

import com.example.chat.dto.CreateGroup;
import com.example.chat.dto.GrantRequest;
import com.example.chat.dto.GroupResponse;
import com.example.chat.dto.UpdateGroup;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import com.example.chat.dto.GrantView;

import java.util.List;
import java.util.Objects;

@Service
public class GroupService {
    private final AppGroupRepository groupRepo;
    private final AppUserRepository userRepo;
    private final GroupPermissionRepository permRepo; // ← EKLENDİ

    public GroupService(AppGroupRepository groupRepo, AppUserRepository userRepo,  GroupPermissionRepository permRepo) {
        this.groupRepo = groupRepo;
        this.userRepo = userRepo;
        this.permRepo = permRepo;
    }

    @Transactional
    public GroupResponse create(CreateGroup req, String subject,boolean isAdmin) {
        if (req.name() == null || req.name().isBlank())
            throw new IllegalArgumentException("Group name is required");
        if (groupRepo.existsByName(req.name()))
            throw new IllegalArgumentException("Group name already exists");

        // JWT subject ile (keycloakId) kurucuyu bul
        var creator = userRepo.findByKeycloakId(subject)
                .orElseThrow(() -> new IllegalStateException("Creator user not found"));

        AppGroup g = new AppGroup();
        g.setName(req.name());
        g.setCreatedByKeycloakId(subject);
        g.setAdminOwned(isAdmin); // admin açarsa kilitli grup mantığı

        // Dışarıdan gelen userIds varsa ekle
        if (req.userIds() != null && !req.userIds().isEmpty()) {
            var distinctIds = req.userIds().stream().distinct().toList();
            var users = userRepo.findAllById(distinctIds);
            if (users.size() != distinctIds.size())
                throw new IllegalArgumentException("One or more userIds not found");
            g.getMembers().addAll(users);
        }

        // Kurucuyu her durumda üye yap (zaten ekliyse tekrar etmez)
        g.getMembers().add(creator);

        var saved = groupRepo.save(g);
        var memberIds = saved.getMembers().stream().map(AppUser::getId).toList();
        return new GroupResponse(saved.getId(), saved.getName(), memberIds, saved.isAdminOwned());
    }

    @Transactional
    public GroupResponse update(Long id, UpdateGroup req, String subject, boolean isAdmin) {
        var g = groupRepo.findByIdWithMembers(id)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + id));

        var me = userRepo.findByKeycloakId(subject)
                .orElseThrow(() -> new IllegalStateException("Current user not found"));

        // YETKİ: tek kaynaktan
        if (!canUpdate(g, me, isAdmin)) {
            throw new org.springframework.security.access.AccessDeniedException("Not allowed to update this group");
        }

        if (req.name() != null && !req.name().isBlank() && !req.name().equals(g.getName())) {
            if (groupRepo.existsByName(req.name()))
                throw new IllegalArgumentException("Group name already exists");
            g.setName(req.name());
        }

        if (req.userIds() != null) {
            var ids = req.userIds().stream().distinct().toList();
            var users = ids.isEmpty() ? List.<AppUser>of() : userRepo.findAllById(ids);
            if (users.size() != ids.size())
                throw new IllegalArgumentException("One or more userIds not found");

            g.getMembers().clear();
            g.getMembers().addAll(users);

            // kurucuyu asla dışarıda bırakma
            var creator = userRepo.findByKeycloakId(g.getCreatedByKeycloakId())
                    .orElseThrow(() -> new IllegalStateException("Creator user not found"));
            g.getMembers().add(creator);

            // Üyelikten çıkanların grant'lerini temizle (opsiyonel ama önerilir)
            var currentMemberIds = g.getMembers().stream().map(AppUser::getId).collect(java.util.stream.Collectors.toSet());
            permRepo.deleteAllByGroupIdAndUserIdNotIn(g.getId(), currentMemberIds);
        }

        return toDto(g);
    }
    private boolean canUpdate(AppGroup g, AppUser me, boolean isAdmin) {
        if (isAdmin) return true; // admin her zaman
        if (g.isAdminOwned()) {
            return permRepo.findByGroupIdAndUserId(g.getId(), me.getId())
                    .map(GroupPermission::isCanUpdate)
                    .orElse(false);
        }
        if (me.getKeycloakId().equals(g.getCreatedByKeycloakId())) return true; // kurucu
        return permRepo.findByGroupIdAndUserId(g.getId(), me.getId())
                .map(GroupPermission::isCanUpdate)
                .orElse(false);
    }

    @Transactional
    public void delete(Long id, String subject, boolean isAdmin) {
        var g = groupRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + id));

        var me = userRepo.findByKeycloakId(subject)
                .orElseThrow(() -> new IllegalStateException("Current user not found"));

        if (!canDelete(g, me, isAdmin)) {
            throw new org.springframework.security.access.AccessDeniedException("Not allowed to delete this group");
        }

        groupRepo.deleteById(id);
    }

    private boolean canDelete(AppGroup g, AppUser me, boolean isAdmin) {
        if (isAdmin) return true;
        if (g.isAdminOwned()) {
            return permRepo.findByGroupIdAndUserId(g.getId(), me.getId())
                    .map(GroupPermission::isCanDelete)
                    .orElse(false);
        }
        if (me.getKeycloakId().equals(g.getCreatedByKeycloakId())) return true;
        return permRepo.findByGroupIdAndUserId(g.getId(), me.getId())
                .map(GroupPermission::isCanDelete)
                .orElse(false);
    }

    @Transactional
    public GroupResponse getOne(Long id) {
        var g = groupRepo.findByIdWithMembers(id)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + id));
        return toDto(g);
    }

    @Transactional
    public List<GroupResponse> listAll() {
        // üyeleri tek sorguda almak için fetch join kullanıyoruz
        var groups = groupRepo.findAllWithMembers();
        return groups.stream().map(this::toDto).toList();
    }
    private GroupResponse toDto(AppGroup g) {
         var memberIds = g.getMembers().stream().map(AppUser::getId).toList();
        return new GroupResponse(g.getId(), g.getName(), memberIds, g.isAdminOwned());
    }
    @Transactional
    public List<GroupResponse> listMine(Long userId) {
        var groups = groupRepo.findAllByMemberUserId(userId);
        return groups.stream().map(this::toDto).toList();
    }
    @Transactional
    public void grant(Long groupId, String subject, boolean isAdmin, GrantRequest req) {
        var g = groupRepo.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

        boolean isCreator = subject.equals(g.getCreatedByKeycloakId());

        // adminOwned → sadece ADMIN; userOwned → sadece KURUCU grant verebilir
        boolean allowed = g.isAdminOwned() ? isAdmin : isCreator;
        if (!allowed) {
            throw new org.springframework.security.access.AccessDeniedException("Not allowed to grant on this group");
        }

        if (req.userId() == null) throw new IllegalArgumentException("userId is required");

        var target = userRepo.findById(req.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + req.userId()));

        // ❗ ADMIN kullanıcılara explicit grant yasak
        if (isAdminUser(target)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "ADMIN users have implicit full permissions; their grants cannot be changed");
        }

        // Üye olmayan kişiye grant verme
        if (g.getMembers().stream().noneMatch(u -> u.getId().equals(target.getId()))) {
            throw new IllegalArgumentException("User must be a member of the group to receive grants");
        }

        // Kurucunun yetkisini sadece kurucu değiştirebilir (userOwned için)
        if (!isCreator && target.getKeycloakId().equals(g.getCreatedByKeycloakId()) && !g.isAdminOwned()) {
            throw new org.springframework.security.access.AccessDeniedException("Creator permissions can only be changed by creator");
        }

        var gp = permRepo.findByGroupIdAndUserId(g.getId(), target.getId())
                .orElse(new GroupPermission(g.getId(), target.getId(), false, false));

        boolean touched = false;
        if (req.canUpdate() != null) { gp.setCanUpdate(req.canUpdate()); touched = true; }
        if (req.canDelete() != null) { gp.setCanDelete(req.canDelete()); touched = true; }
        if (!touched) throw new IllegalArgumentException("At least one of canUpdate/canDelete must be provided");

        permRepo.save(gp);
    }

    @Transactional
    public void revoke(Long groupId, String subject, boolean isAdmin, Long userId) {
        var g = groupRepo.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

        boolean isCreator = subject.equals(g.getCreatedByKeycloakId());

        // adminOwned → sadece ADMIN; userOwned → sadece KURUCU revoke
        boolean allowed = g.isAdminOwned() ? isAdmin : isCreator;
        if (!allowed) {
            throw new org.springframework.security.access.AccessDeniedException("Not allowed to revoke on this group");
        }

        var target = userRepo.findById(userId).orElse(null);

        // ❗ ADMIN kullanıcılara explicit grant yasak (revoke denemesi de engellenir)
        if (target != null && isAdminUser(target)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "ADMIN users have implicit full permissions; their grants cannot be changed");
        }

        // Kurucu grant'ını sadece kurucu kaldırabilir (userOwned)
        if (target != null && target.getKeycloakId().equals(g.getCreatedByKeycloakId()) && !isCreator && !g.isAdminOwned()) {
            throw new org.springframework.security.access.AccessDeniedException("Creator permissions cannot be revoked by non-creator");
        }

        permRepo.deleteByGroupIdAndUserId(groupId, userId);
    }

    public record Can(boolean canUpdate, boolean canDelete, boolean canGrant) {}

    @Transactional
    public Can myPermissions(Long groupId, String subject, boolean isAdmin) {
        var g  = groupRepo.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
        var me = userRepo.findByKeycloakId(subject)
                .orElseThrow(() -> new IllegalStateException("Current user not found"));

        boolean cu = canUpdate(g, me, isAdmin);
        boolean cd = canDelete(g, me, isAdmin);

        // Grant edebilecek tek kişi:
        // adminOwned  → ADMIN
        // userOwned   → KURUCU
        boolean cg = g.isAdminOwned() ? isAdmin : subject.equals(g.getCreatedByKeycloakId());

        return new Can(cu, cd, cg);
    }

    @Transactional
    public List<GrantView> listGrants(Long groupId, String subject, boolean isAdmin) {
        var g = groupRepo.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

        // Kimler görebilir? admin-owned ise admin; değilse admin veya kurucu
        if (g.isAdminOwned() ? !isAdmin : !(isAdmin || subject.equals(g.getCreatedByKeycloakId()))) {
            throw new org.springframework.security.access.AccessDeniedException("Not allowed to list grants");
        }

        return permRepo.findAllByGroupId(groupId).stream()
                .map(p -> new GrantView(p.getUserId(), p.isCanUpdate(), p.isCanDelete()))
                .toList();
    }
    @Transactional
    public AppGroup getEntity(Long id) {
        return groupRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Group not found: " + id));
    }
    private boolean isAdminUser(AppUser u) {
        if (u == null) return false;
        // AppUser.role gibi bir alanın olduğunu varsayıyorum.
        // Yoksa kendi alan/adaptörünü kullan.
        var role = String.valueOf(u.getRole());
        return "ADMIN".equalsIgnoreCase(role);
    }

}