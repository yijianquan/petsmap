package com.wujia.pet.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "walk_group_message", indexes = @Index(name = "idx_walk_message_group_id", columnList = "group_id,id"))
public class WalkGroupMessage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "group_id", nullable = false)
    private WalkGroup group;
    @ManyToOne(optional = false) @JoinColumn(name = "sender_id", nullable = false)
    private UserAccount sender;
    @Column(nullable = false, length = 1000)
    private String content;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public WalkGroup getGroup() { return group; }
    public void setGroup(WalkGroup group) { this.group = group; }
    public UserAccount getSender() { return sender; }
    public void setSender(UserAccount sender) { this.sender = sender; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
