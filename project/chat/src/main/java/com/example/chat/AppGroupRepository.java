// GroupRepository.java
package com.example.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppGroupRepository extends JpaRepository<AppGroup, Long> {
    boolean existsByName(String name);
    @Query("select distinct g from AppGroup g left join fetch g.members")
    List<AppGroup> findAllWithMembers();

    @Query("select g from AppGroup g left join fetch g.members where g.id = :id")
    Optional<AppGroup> findByIdWithMembers(@Param("id") Long id);

    @Query("""
      select distinct g
      from AppGroup g
      join g.members m
      where m.id = :userId
    """)
    List<AppGroup> findAllByMemberUserId(Long userId);

}
