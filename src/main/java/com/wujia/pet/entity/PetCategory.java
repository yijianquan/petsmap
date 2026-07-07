package com.wujia.pet.entity;

public enum PetCategory {
    CAT("猫咪"),
    DOG("狗狗"),
    OTHER("其他");

    private final String displayName;

    PetCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
