// GroupController.java
package com.example.chat;

import com.example.chat.dto.CreateGroup;
import com.example.chat.dto.GrantRequest;
import com.example.chat.dto.GrantView;
import com.example.chat.dto.GroupResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class GroupController {
    private final GroupService groupService;
    private final AppUserRepository userRepo;
     public GroupController(GroupService groupService,AppUserRepository userRepo) { this.groupService = groupService;
         this.userRepo = userRepo;

    }
    private boolean isAdmin(Jwt jwt) {
        String role = JwtUtil.extractMainRole(jwt);
         if (role.contains("admin")){
            return true;
        }
        return false;
    }
    @PostMapping("/group")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse createGroup(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateGroup req) {
        return groupService.create(req, jwt.getSubject(), isAdmin(jwt));
    }
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/group")
    public List<GroupResponse> listGroups() {
        return groupService.listAll();
    }
    @GetMapping("/group/{id}")
    public GroupResponse getGroup(@PathVariable Long id) {
        return groupService.getOne(id);
    }
    @GetMapping("/my-groups")
    public List<GroupResponse> listMyGroups(@AuthenticationPrincipal Jwt jwt) {
        var me = userRepo.findByKeycloakId(jwt.getSubject())
                .orElseThrow(() -> new IllegalStateException("Current user not found"));
        return groupService.listMine(me.getId());
    }
    @PutMapping("/group/{id}")
    public GroupResponse updateGroup(@PathVariable Long id,
                                     @AuthenticationPrincipal Jwt jwt,
                                     @RequestBody com.example.chat.dto.UpdateGroup req) {
        return groupService.update(id, req, jwt.getSubject(), isAdmin(jwt));
    }

    @DeleteMapping("/group/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        groupService.delete(id, jwt.getSubject(), isAdmin(jwt));
    }
    @PutMapping("/group/{id}/grants")
    public void grant(@PathVariable Long id,
                      @AuthenticationPrincipal Jwt jwt,
                      @RequestBody GrantRequest req) {
        groupService.grant(id, jwt.getSubject(), isAdmin(jwt), req);
    }

    @DeleteMapping("/group/{id}/grants/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable Long id,
                       @PathVariable Long userId,
                       @AuthenticationPrincipal Jwt jwt) {
        groupService.revoke(id, jwt.getSubject(), isAdmin(jwt), userId);
    }

    @GetMapping("/group/{id}/permissions")
    public Map<String, Boolean> myPerms(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        boolean admin = isAdmin(jwt);
        var can = groupService.myPermissions(id, jwt.getSubject(), admin);

        var g = groupService.getEntity(id);
        boolean isCreator = Objects.equals(g.getCreatedByKeycloakId(), jwt.getSubject());

        boolean canGrant = g.isAdminOwned() ? admin : (admin || isCreator);

        return Map.of(
                "canUpdate", can.canUpdate(),
                "canDelete", can.canDelete(),
                "canGrant",  can.canGrant()
        );
    }

    @GetMapping("/group/{id}/grants")
    public List<GrantView> listGrants(@PathVariable Long id,
                                      @AuthenticationPrincipal Jwt jwt) {
        return groupService.listGrants(id, jwt.getSubject(), isAdmin(jwt));
    }

}
