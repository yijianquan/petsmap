package com.wujia.pet.entity;

public enum PlaceType {
    RESTAURANT("餐厅", true),
    MALL("逛街", true),
    HOTEL("住宿", true),
    PARK("散步", true),
    PET_STORE("宠物店", true),
    HOSPITAL("医院", true),
    SCENIC("散步", false),
    LAWN("散步", false);

    private final String displayName;
    private final boolean visible;

    PlaceType(String displayName, boolean visible) {
        this.displayName = displayName;
        this.visible = visible;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isVisible() {
        return visible;
    }

    public PlaceType categoryType() {
        if (this == SCENIC || this == LAWN) {
            return PARK;
        }
        return this;
    }

    public static PlaceType[] visibleValues() {
        return new PlaceType[] { RESTAURANT, MALL, HOTEL, PARK, PET_STORE, HOSPITAL };
    }
}
