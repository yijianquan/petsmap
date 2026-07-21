package com.wujia.pet.service;

import com.wujia.pet.entity.PlaceType;

public record MapSearchResult(
        String name,
        String address,
        Double latitude,
        Double longitude,
        String cityName,
        PlaceType type,
        String typeDisplayName,
        String provider) {
}
