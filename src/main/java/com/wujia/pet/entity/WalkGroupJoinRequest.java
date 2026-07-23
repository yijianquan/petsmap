package com.wujia.pet.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "walk_group_join_request", indexes = {
        @Index(name = "idx_walk_join_request_group_status", columnList = "group_id,status")
})
public class WalkGroupJoinRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "group_id", nullable = false)
    private WalkGroup group;
    @ManyToOne(optional = false) @JoinColumn(name = "applicant_id", nullable = false)
    private UserAccount applicant;
    @ManyToOne(optional = false) @JoinColumn(name = "inviter_id", nullable = false)
    private UserAccount inviter;
    @Column(nullable = false, length = 16)
    private String status = "PENDING";
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    public Long getId(){return id;} public WalkGroup getGroup(){return group;} public void setGroup(WalkGroup v){group=v;}
    public UserAccount getApplicant(){return applicant;} public void setApplicant(UserAccount v){applicant=v;}
    public UserAccount getInviter(){return inviter;} public void setInviter(UserAccount v){inviter=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
    public LocalDateTime getHandledAt(){return handledAt;} public void setHandledAt(LocalDateTime v){handledAt=v;}
}
