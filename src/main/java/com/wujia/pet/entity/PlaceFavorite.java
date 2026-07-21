package com.wujia.pet.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(
        uniqueConstraints = @UniqueConstraint(name = "uk_favorite_user_place", columnNames = {"user_id", "place_id"}),
        indexes = {
                @Index(name = "idx_favorite_user_created", columnList = "user_id,created_at"),
                @Index(name = "idx_favorite_place", columnList = "place_id")
        })
public class PlaceFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private UserAccount user;

    @ManyToOne(optional = false)
    private PetFriendlyPlace place;

    private LocalDateTime createdAt = LocalDateTime.now();

    public Long getId() { return id; }
    public UserAccount getUser() { return user; }
    public void setUser(UserAccount user) { this.user = user; }
    public PetFriendlyPlace getPlace() { return place; }
    public void setPlace(PetFriendlyPlace place) { this.place = place; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
