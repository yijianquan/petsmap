package com.wujia.pet.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "walk_group", uniqueConstraints = {
        @UniqueConstraint(name = "uk_walk_group_place_name", columnNames = {"place_id", "name"}),
        @UniqueConstraint(name = "uk_walk_group_place_owner", columnNames = {"place_id", "owner_id"})
})
public class WalkGroup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 40)
    private String name;
    @ManyToOne(optional = false) @JoinColumn(name = "place_id", nullable = false)
    private PetFriendlyPlace place;
    @ManyToOne(optional = false) @JoinColumn(name = "owner_id", nullable = false)
    private UserAccount owner;
    @Column(name = "city_code", nullable = false, length = 30)
    private String cityCode = "";
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WalkGroupMember> members = new ArrayList<>();
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WalkGroupMessage> messages = new ArrayList<>();

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public PetFriendlyPlace getPlace() { return place; }
    public void setPlace(PetFriendlyPlace place) { this.place = place; }
    public UserAccount getOwner() { return owner; }
    public void setOwner(UserAccount owner) { this.owner = owner; }
    public String getCityCode() { return cityCode; }
    public void setCityCode(String cityCode) { this.cityCode = cityCode == null ? "" : cityCode.trim(); }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
