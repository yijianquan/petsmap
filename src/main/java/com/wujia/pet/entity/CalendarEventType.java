package com.wujia.pet.entity;

public enum CalendarEventType {
    VACCINE("疫苗"),
    BATH("洗澡"),
    BIRTHDAY("节日提醒");

    private final String displayName;

    CalendarEventType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
