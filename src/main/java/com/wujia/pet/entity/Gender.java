package com.wujia.pet.entity;

public enum Gender {
    MALE("♂ 男孩"),
    FEMALE("♀ 女孩"),
    UNKNOWN("未知");

    private final String displayName;

    Gender(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
