package com.wujia.pet.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "walk_group_member", uniqueConstraints = @UniqueConstraint(name = "uk_walk_group_member", columnNames = {"group_id", "user_id"}))
public class WalkGroupMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "group_id", nullable = false)
    private WalkGroup group;
    @ManyToOne(optional = false) @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;
    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt = LocalDateTime.now();

    public Long getId() { return id; }
    public WalkGroup getGroup() { return group; }
    public void setGroup(WalkGroup group) { this.group = group; }
    public UserAccount getUser() { return user; }
    public void setUser(UserAccount user) { this.user = user; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
}
