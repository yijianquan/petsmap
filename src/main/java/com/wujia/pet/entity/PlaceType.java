package com.wujia.pet.entity;

public enum PlaceType {
    PARK("大自然"),
    SCENIC("景点"),
    MALL("商场"),
    HOTEL("酒店"),
    LAWN("草坪"),
    RESTAURANT("宠物友好餐厅");

    private final String displayName;

    PlaceType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
