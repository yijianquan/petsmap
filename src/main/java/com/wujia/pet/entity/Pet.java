package com.wujia.pet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDate;
import java.time.Period;

@Entity
public class Pet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, length = 80)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PetCategory category = PetCategory.OTHER;

    @Column(length = 500)
    private String avatarUrl;

    @Column(length = 100)
    private String avatarContentType;

    @JsonIgnore
    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] avatarData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gender gender = Gender.UNKNOWN;

    @NotNull
    @Column(nullable = false)
    private LocalDate birthday;

    @ManyToOne(optional = false)
    private UserAccount owner;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public PetCategory getCategory() {
        return category;
    }

    public void setCategory(PetCategory category) {
        this.category = category == null ? PetCategory.OTHER : category;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getAvatarContentType() {
        return avatarContentType;
    }

    public void setAvatarContentType(String avatarContentType) {
        this.avatarContentType = avatarContentType;
    }

    public byte[] getAvatarData() {
        return avatarData;
    }

    public void setAvatarData(byte[] avatarData) {
        this.avatarData = avatarData;
    }

    @JsonIgnore
    public boolean hasAvatarData() {
        return avatarData != null && avatarData.length > 0;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public void setBirthday(LocalDate birthday) {
        this.birthday = birthday;
    }

    public UserAccount getOwner() {
        return owner;
    }

    public void setOwner(UserAccount owner) {
        this.owner = owner;
    }

    public String getAgeText() {
        if (birthday == null) {
            return "未知年龄";
        }
        LocalDate today = LocalDate.now();
        if (birthday.isAfter(today)) {
            return "未出生";
        }
        Period age = Period.between(birthday, today);
        if (age.getYears() > 0) {
            return age.getMonths() > 0
                    ? age.getYears() + "岁" + age.getMonths() + "个月"
                    : age.getYears() + "岁";
        }
        if (age.getMonths() > 0) {
            return age.getDays() > 0
                    ? age.getMonths() + "个月" + age.getDays() + "天"
                    : age.getMonths() + "个月";
        }
        return Math.max(age.getDays(), 0) + "天";
    }
}
