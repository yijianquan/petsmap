package com.wujia.pet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotBlank;
import java.util.Arrays;
import java.util.List;

@Entity
public class PetFriendlyPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaceType type = PlaceType.PARK;

    @Column(length = 240)
    private String address;

    private Double latitude;

    private Double longitude;

    @Column(length = 500)
    private String description;

    @Column(length = 800)
    private String tags;

    private boolean indoorAllowed;

    private boolean largeDogFriendly;

    private boolean catFriendly;

    private boolean leashRequired;

    private boolean waterAvailable;

    private boolean parkingAvailable;

    private boolean feeRequired;

    @Column(length = 500)
    private String policyNote;

    @ManyToOne(optional = false)
    private UserAccount uploadedBy;

    @Transient
    private double averageRating;

    @Transient
    private long commentCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PlaceType getType() {
        return type;
    }

    public void setType(PlaceType type) {
        this.type = type;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public List<String> getTagList() {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .distinct()
                .toList();
    }

    public boolean isIndoorAllowed() {
        return indoorAllowed;
    }

    public void setIndoorAllowed(boolean indoorAllowed) {
        this.indoorAllowed = indoorAllowed;
    }

    public boolean isLargeDogFriendly() {
        return largeDogFriendly;
    }

    public void setLargeDogFriendly(boolean largeDogFriendly) {
        this.largeDogFriendly = largeDogFriendly;
    }

    public boolean isCatFriendly() {
        return catFriendly;
    }

    public void setCatFriendly(boolean catFriendly) {
        this.catFriendly = catFriendly;
    }

    public boolean isLeashRequired() {
        return leashRequired;
    }

    public void setLeashRequired(boolean leashRequired) {
        this.leashRequired = leashRequired;
    }

    public boolean isWaterAvailable() {
        return waterAvailable;
    }

    public void setWaterAvailable(boolean waterAvailable) {
        this.waterAvailable = waterAvailable;
    }

    public boolean isParkingAvailable() {
        return parkingAvailable;
    }

    public void setParkingAvailable(boolean parkingAvailable) {
        this.parkingAvailable = parkingAvailable;
    }

    public boolean isFeeRequired() {
        return feeRequired;
    }

    public void setFeeRequired(boolean feeRequired) {
        this.feeRequired = feeRequired;
    }

    public String getPolicyNote() {
        return policyNote;
    }

    public void setPolicyNote(String policyNote) {
        this.policyNote = policyNote;
    }

    public UserAccount getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(UserAccount uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    public double getAverageRating() {
        return averageRating;
    }

    public void setAverageRating(double averageRating) {
        this.averageRating = averageRating;
    }

    public long getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(long commentCount) {
        this.commentCount = commentCount;
    }

    public String getRatingText() {
        return commentCount == 0 ? "暂无评分" : String.format("%.1f", averageRating);
    }
}
