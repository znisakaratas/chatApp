package com.example.chat;

import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;
import java.util.HashSet; import java.util.Set;

@Getter @Setter
@Entity @Table(name = "app_group")
public class AppGroup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="name", nullable=false, unique=true)
    private String name;

    @ManyToMany
    @JoinTable(
            name = "group_member",
            joinColumns = @JoinColumn(name = "group_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<AppUser> members = new HashSet<>();
    @Column(name = "created_by", length = 64)
    private String createdByKeycloakId;

    @Column(name = "admin_owned", nullable = false)
    private boolean adminOwned = false;

}