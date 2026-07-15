package com.wujia.pet.entity;

public enum PlaceSourceType {
    USER_UPLOAD("用户上传"),
    IMPORTED("导入采集");

    private final String displayName;

    PlaceSourceType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
