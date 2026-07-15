package com.wujia.pet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wujia.pet.entity.PlaceType;
import com.wujia.pet.repository.PetFriendlyPlaceRepository;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

@Service
public class MapSearchService {

    private final RestClient restClient;
    private final PetFriendlyPlaceRepository placeRepository;

    @Value("${app.map.amap-key:}")
    private String amapKey;

    @Value("${app.map.default-city:上海}")
    private String defaultCity;

    public MapSearchService(RestClient.Builder restClientBuilder, PetFriendlyPlaceRepository placeRepository) {
        this.restClient = restClientBuilder
                .defaultHeader("User-Agent", "WuJiaYouChong/0.0.1")
                .build();
        this.placeRepository = placeRepository;
    }

    public List<MapSearchResult> search(String keyword, String city) {
        String cleanKeyword = keyword == null ? "" : keyword.trim();
        if (cleanKeyword.isEmpty()) {
            return List.of();
        }
        String cleanCity = city == null || city.isBlank() ? defaultCity : city.trim();
        if (amapKey != null && !amapKey.isBlank()) {
            List<MapSearchResult> amapResults = searchAmap(cleanKeyword, cleanCity);
            if (!amapResults.isEmpty()) {
                return amapResults;
            }
        }
        List<MapSearchResult> nominatimResults = searchNominatim(cleanKeyword, cleanCity);
        if (!nominatimResults.isEmpty()) {
            return nominatimResults;
        }
        return searchLocalPlaces(cleanKeyword);
    }

    public Map<String, Object> reverse(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return Map.of();
        }
        if (amapKey == null || amapKey.isBlank()) {
            return Map.of(
                    "name", "当前位置",
                    "address", "",
                    "latitude", latitude,
                    "longitude", longitude);
        }
        URI uri = URI.create("https://restapi.amap.com/v3/geocode/regeo"
                + "?location=" + encode(longitude + "," + latitude)
                + "&extensions=all&radius=500&roadlevel=0"
                + "&key=" + encode(amapKey));
        try {
            JsonNode root = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            JsonNode regeocode = root == null ? null : root.path("regeocode");
            if (regeocode == null || regeocode.isMissingNode()) {
                return Map.of("name", "当前位置", "address", "", "latitude", latitude, "longitude", longitude);
            }
            String address = regeocode.path("formatted_address").asText("");
            String name = firstPoiName(regeocode);
            if (name.isBlank()) {
                name = address.isBlank() ? "当前位置" : address;
            }
            return Map.of(
                    "name", name,
                    "address", address,
                    "latitude", latitude,
                    "longitude", longitude);
        } catch (RuntimeException exception) {
            return Map.of("name", "当前位置", "address", "", "latitude", latitude, "longitude", longitude);
        }
    }

    private List<MapSearchResult> searchAmap(String keyword, String city) {
        String encodedKeyword = encode(keyword);
        String encodedCity = encode(city);
        URI uri = URI.create("https://restapi.amap.com/v3/place/text"
                + "?keywords=" + encodedKeyword
                + "&city=" + encodedCity
                + "&offset=10&page=1&extensions=base"
                + "&key=" + encode(amapKey));
        try {
            JsonNode root = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (root == null || !"1".equals(root.path("status").asText())) {
                return List.of();
            }
            List<MapSearchResult> results = new ArrayList<>();
            for (JsonNode poi : root.path("pois")) {
                String location = poi.path("location").asText("");
                String[] parts = location.split(",");
                if (parts.length != 2) {
                    continue;
                }
                Double longitude = parseDouble(parts[0]);
                Double latitude = parseDouble(parts[1]);
                if (latitude == null || longitude == null) {
                    continue;
                }
                String name = poi.path("name").asText("");
                String address = joinAddress(
                        poi.path("pname").asText(""),
                        poi.path("cityname").asText(""),
                        poi.path("adname").asText(""),
                        poi.path("address").asText(""));
                PlaceType type = inferType(name + " " + poi.path("type").asText(""));
                results.add(result(name, address, latitude, longitude, type, "amap"));
            }
            return results;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private List<MapSearchResult> searchNominatim(String keyword, String city) {
        String query = city + " " + keyword;
        URI uri = URI.create("https://nominatim.openstreetmap.org/search"
                + "?format=jsonv2&addressdetails=1&limit=10&q=" + encode(query));
        try {
            JsonNode root = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (root == null || !root.isArray()) {
                return List.of();
            }
            List<MapSearchResult> results = new ArrayList<>();
            for (JsonNode item : root) {
                Double latitude = parseDouble(item.path("lat").asText(""));
                Double longitude = parseDouble(item.path("lon").asText(""));
                if (latitude == null || longitude == null) {
                    continue;
                }
                String name = item.path("name").asText("");
                if (name.isBlank()) {
                    name = item.path("display_name").asText(keyword).split(",")[0];
                }
                String address = item.path("display_name").asText("");
                PlaceType type = inferType(name + " " + item.path("type").asText("") + " " + item.path("class").asText(""));
                results.add(result(name, address, latitude, longitude, type, "nominatim"));
            }
            return results;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private List<MapSearchResult> searchLocalPlaces(String keyword) {
        return placeRepository.findTop10ByNameContainingOrderByIdDesc(keyword).stream()
                .filter(place -> place.getLatitude() != null && place.getLongitude() != null)
                .map(place -> result(
                        place.getName(),
                        place.getAddress(),
                        place.getLatitude(),
                        place.getLongitude(),
                place.getType().categoryType(),
                        "local"))
                .toList();
    }

    private MapSearchResult result(
            String name,
            String address,
            Double latitude,
            Double longitude,
            PlaceType type,
            String provider) {
        return new MapSearchResult(
                name,
                address,
                latitude,
                longitude,
                type.categoryType(),
                type.categoryType().getDisplayName(),
                provider);
    }

    private PlaceType inferType(String text) {
        String value = text == null ? "" : text.toLowerCase();
        if (value.contains("宠物店") || value.contains("宠物用品") || value.contains("宠物生活馆")
                || value.contains("pet store") || value.contains("pet shop")) {
            return PlaceType.PET_STORE;
        }
        if (value.contains("宠物医院") || value.contains("动物医院") || value.contains("兽医")
                || value.contains("veterinary") || value.contains("vets") || value.contains("vet")) {
            return PlaceType.HOSPITAL;
        }
        if (value.contains("酒店") || value.contains("hotel")) {
            return PlaceType.HOTEL;
        }
        if (value.contains("餐厅") || value.contains("餐饮") || value.contains("饭店") || value.contains("restaurant") || value.contains("food")) {
            return PlaceType.RESTAURANT;
        }
        if (value.contains("商场") || value.contains("购物") || value.contains("mall") || value.contains("shopping")) {
            return PlaceType.MALL;
        }
        if (value.contains("景点") || value.contains("观景") || value.contains("风景") || value.contains("attraction")) {
            return PlaceType.PARK;
        }
        if (value.contains("草坪") || value.contains("lawn") || value.contains("绿地")) {
            return PlaceType.PARK;
        }
        return PlaceType.PARK;
    }

    private String joinAddress(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank() || "[]".equals(part)) {
                continue;
            }
            builder.append(part);
        }
        return builder.toString();
    }

    private String firstPoiName(JsonNode regeocode) {
        JsonNode pois = regeocode.path("pois");
        if (pois.isArray() && !pois.isEmpty()) {
            return pois.get(0).path("name").asText("");
        }
        JsonNode aois = regeocode.path("aois");
        if (aois.isArray() && !aois.isEmpty()) {
            return aois.get(0).path("name").asText("");
        }
        return "";
    }

    private String encode(String value) {
        return UriUtils.encode(value, StandardCharsets.UTF_8);
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Coordinate gcj02ToWgs84(double latitude, double longitude) {
        if (outsideChina(latitude, longitude)) {
            return new Coordinate(latitude, longitude);
        }
        Coordinate delta = transform(latitude, longitude);
        return new Coordinate(latitude * 2 - delta.latitude(), longitude * 2 - delta.longitude());
    }

    private Coordinate transform(double latitude, double longitude) {
        double dLat = transformLat(longitude - 105.0, latitude - 35.0);
        double dLng = transformLng(longitude - 105.0, latitude - 35.0);
        double radLat = latitude / 180.0 * Math.PI;
        double magic = Math.sin(radLat);
        magic = 1 - 0.00669342162296594323 * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((6335552.717000426 * magic) / (sqrtMagic * sqrtMagic) * Math.PI);
        dLng = (dLng * 180.0) / (6378245.0 / sqrtMagic * Math.cos(radLat) * Math.PI);
        return new Coordinate(latitude + dLat, longitude + dLng);
    }

    private boolean outsideChina(double latitude, double longitude) {
        return longitude < 72.004 || longitude > 137.8347 || latitude < 0.8293 || latitude > 55.8271;
    }

    private double transformLat(double x, double y) {
        double result = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y
                + 0.2 * Math.sqrt(Math.abs(x));
        result += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        result += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
        result += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        return result;
    }

    private double transformLng(double x, double y) {
        double result = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y
                + 0.1 * Math.sqrt(Math.abs(x));
        result += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        result += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
        result += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        return result;
    }

    private record Coordinate(double latitude, double longitude) {
    }
}
