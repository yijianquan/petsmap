package com.wujia.pet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wujia.pet.entity.PetFriendlyPlace;
import com.wujia.pet.entity.PlaceType;
import com.wujia.pet.entity.SysArea;
import com.wujia.pet.entity.UserAccount;
import com.wujia.pet.repository.PetFriendlyPlaceRepository;
import com.wujia.pet.repository.SysAreaRepository;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

@Service
public class PlaceCrawlService {

    private static final int PAGE_SIZE = 20;
    private static final int MAX_PAGE = 3;
    private static final String PROVIDER = "AMAP";

    private final RestClient restClient;
    private final PetFriendlyPlaceRepository placeRepository;
    private final SysAreaRepository sysAreaRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private volatile CrawlStatus status = CrawlStatus.idle();

    @Value("${app.map.amap-key:}")
    private String amapKey;

    @Value("${app.map.default-city:上海市}")
    private String defaultCity;

    public PlaceCrawlService(
            RestClient.Builder restClientBuilder,
            PetFriendlyPlaceRepository placeRepository,
            SysAreaRepository sysAreaRepository) {
        this.restClient = restClientBuilder
                .defaultHeader("User-Agent", "ChongQuXing/0.0.1")
                .build();
        this.placeRepository = placeRepository;
        this.sysAreaRepository = sysAreaRepository;
    }

    public synchronized CrawlStatus start(String city, UserAccount uploader) {
        return start(city, null, uploader);
    }

    public synchronized CrawlStatus start(SysArea sysArea, UserAccount uploader) {
        String city = sysArea == null ? "" : crawlCityName(sysArea);
        return start(city, sysArea, uploader);
    }

    private synchronized CrawlStatus start(String city, SysArea sysArea, UserAccount uploader) {
        if (status.running()) {
            return status;
        }
        String cleanCity = text(city).isBlank() ? defaultCity : text(city);
        if (amapKey == null || amapKey.isBlank()) {
            status = CrawlStatus.failed(cleanCity, "未配置高德 Web 服务 Key，无法采集地点。");
            return status;
        }
        String batchId = UUID.randomUUID().toString().replace("-", "");
        status = CrawlStatus.running(cleanCity, batchId);
        CompletableFuture.runAsync(() -> run(cleanCity, sysArea, batchId, uploader), executor);
        return status;
    }

    public CrawlStatus status() {
        return status;
    }

    public String defaultCity() {
        return defaultCity;
    }

    private void run(String city, SysArea sysArea, String batchId, UserAccount uploader) {
        int fetched = 0;
        int inserted = 0;
        int skipped = 0;
        try {
            ExistingPlaces existing = loadExistingPlaces();
            for (CrawlPlan plan : crawlPlans()) {
                updateProgress(city, batchId, fetched, inserted, skipped, "正在采集：" + plan.keyword());
                for (int page = 1; page <= MAX_PAGE; page++) {
                    List<CrawlCandidate> candidates = searchAmap(city, sysArea, plan, page);
                    if (candidates.isEmpty()) {
                        break;
                    }
                    fetched += candidates.size();
                    for (CrawlCandidate candidate : candidates) {
                        if (isDuplicate(candidate, existing)) {
                            skipped++;
                            continue;
                        }
                        PetFriendlyPlace place = toPlace(candidate, sysArea, batchId, uploader);
                        placeRepository.save(place);
                        existing.add(candidate);
                        inserted++;
                    }
                    updateProgress(city, batchId, fetched, inserted, skipped, "正在采集：" + plan.keyword());
                }
            }
            status = new CrawlStatus(false, city, batchId, fetched, inserted, skipped,
                    "采集完成，新增 " + inserted + " 个地点，跳过 " + skipped + " 个重复地点。",
                    status.startedAt(), LocalDateTime.now());
        } catch (RuntimeException exception) {
            status = new CrawlStatus(false, city, batchId, fetched, inserted, skipped,
                    "采集失败：" + readableMessage(exception), status.startedAt(), LocalDateTime.now());
        }
    }

    private void updateProgress(String city, String batchId, int fetched, int inserted, int skipped, String message) {
        status = new CrawlStatus(true, city, batchId, fetched, inserted, skipped, message, status.startedAt(), null);
    }

    private ExistingPlaces loadExistingPlaces() {
        Set<String> sourceKeys = new HashSet<>();
        Set<String> nameAddressKeys = new HashSet<>();
        for (PetFriendlyPlace place : placeRepository.findAll()) {
            if (!text(place.getSourceProvider()).isBlank() && !text(place.getSourcePoiId()).isBlank()) {
                sourceKeys.add(sourceKey(place.getSourceProvider(), place.getSourcePoiId()));
            }
            nameAddressKeys.add(nameAddressKey(place.getName(), place.getAddress()));
        }
        return new ExistingPlaces(sourceKeys, nameAddressKeys);
    }

    private boolean isDuplicate(CrawlCandidate candidate, ExistingPlaces existing) {
        if (!candidate.sourcePoiId().isBlank()
                && existing.sourceKeys().contains(sourceKey(candidate.sourceProvider(), candidate.sourcePoiId()))) {
            return true;
        }
        return existing.nameAddressKeys().contains(nameAddressKey(candidate.name(), candidate.address()));
    }

    private List<CrawlCandidate> searchAmap(String city, SysArea sysArea, CrawlPlan plan, int page) {
        List<CrawlCandidate> strictResults = queryAmap(plan.keyword(), city, true, sysArea, plan, page);
        if (!strictResults.isEmpty() || sysArea == null) {
            return strictResults;
        }
        return queryAmap(crawlCityName(sysArea) + " " + plan.keyword(), "", false, sysArea, plan, page);
    }

    private List<CrawlCandidate> queryAmap(
            String keyword,
            String city,
            boolean cityLimit,
            SysArea sysArea,
            CrawlPlan plan,
            int page) {
        URI uri = URI.create("https://restapi.amap.com/v3/place/text"
                + "?keywords=" + encode(keyword)
                + "&city=" + encode(city)
                + "&citylimit=" + cityLimit
                + "&offset=" + PAGE_SIZE
                + "&page=" + page
                + "&extensions=base"
                + "&key=" + encode(amapKey));
        JsonNode root = restClient.get().uri(uri).retrieve().body(JsonNode.class);
        if (root == null || !"1".equals(root.path("status").asText())) {
            return List.of();
        }
        List<CrawlCandidate> results = new ArrayList<>();
        for (JsonNode poi : root.path("pois")) {
            if (!matchesTargetCity(poi, sysArea)) {
                continue;
            }
            CrawlCandidate candidate = candidateOf(poi, plan);
            if (candidate != null) {
                results.add(candidate);
            }
        }
        return results;
    }

    private boolean matchesTargetCity(JsonNode poi, SysArea sysArea) {
        if (sysArea == null) {
            return true;
        }
        String targetCityName = normalizeCityName(crawlCityName(sysArea));
        String poiProvince = normalizeCityName(poi.path("pname").asText(""));
        String poiCity = normalizeCityName(poi.path("cityname").asText(""));
        String poiDistrict = normalizeCityName(poi.path("adname").asText(""));
        String poiAddress = normalizeCityName(poi.path("address").asText(""));
        String poiText = poiProvince + poiCity + poiDistrict + poiAddress;
        if (!targetCityName.isBlank()
                && (targetCityName.equals(poiCity)
                || targetCityName.equals(poiProvince)
                || poiText.contains(targetCityName))) {
            return true;
        }
        String targetCityCode = text(sysArea.getCityCode());
        String poiCityCode = text(poi.path("citycode").asText(""));
        if (!targetCityCode.isBlank() && targetCityCode.equals(poiCityCode)) {
            return true;
        }
        String targetPrefix = adcodePrefix(sysArea.getAdcode());
        String poiAdcode = text(poi.path("adcode").asText(""));
        return targetPrefix.isBlank() || (!poiAdcode.isBlank() && poiAdcode.startsWith(targetPrefix));
    }

    private String normalizeCityName(String value) {
        return text(value)
                .replace("[]", "")
                .replace("　", "")
                .replace(" ", "");
    }

    private String adcodePrefix(Long adcode) {
        if (adcode == null || adcode <= 0) {
            return "";
        }
        String clean = String.valueOf(adcode);
        return clean.length() < 4 ? "" : clean.substring(0, 4);
    }

    private String crawlCityName(SysArea sysArea) {
        String name = text(sysArea.getName());
        if (isMunicipalChildName(name) && sysArea.getPid() != null && sysArea.getPid() > 0) {
            return sysAreaRepository.findByAdcode(sysArea.getPid())
                    .map(SysArea::getName)
                    .map(PlaceCrawlService::text)
                    .filter(parentName -> !parentName.isBlank())
                    .orElse(name);
        }
        return name;
    }

    private boolean isMunicipalChildName(String name) {
        return "市辖区".equals(name) || "县".equals(name);
    }

    private CrawlCandidate candidateOf(JsonNode poi, CrawlPlan plan) {
        String name = text(poi.path("name").asText(""));
        String location = text(poi.path("location").asText(""));
        String[] parts = location.split(",");
        if (name.isBlank() || parts.length != 2) {
            return null;
        }
        Double longitude = parseDouble(parts[0]);
        Double latitude = parseDouble(parts[1]);
        if (latitude == null || longitude == null) {
            return null;
        }
        String poiType = text(poi.path("type").asText(""));
        String address = joinAddress(
                poi.path("pname").asText(""),
                poi.path("cityname").asText(""),
                poi.path("adname").asText(""),
                poi.path("address").asText(""));
        PlaceType type = inferType(name + " " + poiType + " " + address + " " + plan.keyword(), plan.typeHint())
                .categoryType();
        List<String> tags = tagsOf(type, name + " " + address + " " + poiType + " " + plan.keyword());
        String description = descriptionOf(type, poiType, plan.keyword());
        return new CrawlCandidate(
                PROVIDER,
                text(poi.path("id").asText("")),
                plan.keyword(),
                name,
                type,
                address,
                latitude,
                longitude,
                tags,
                description);
    }

    private PetFriendlyPlace toPlace(CrawlCandidate candidate, SysArea sysArea, String batchId, UserAccount uploader) {
        PetFriendlyPlace place = new PetFriendlyPlace();
        place.setName(candidate.name());
        place.setType(candidate.type().categoryType());
        place.setAddress(candidate.address());
        place.setLatitude(candidate.latitude());
        place.setLongitude(candidate.longitude());
        place.setTags(String.join(",", candidate.tags()));
        place.setDescription(candidate.description());
        place.setSourceProvider(candidate.sourceProvider());
        place.setSourcePoiId(candidate.sourcePoiId());
        place.setSourceKeyword(candidate.sourceKeyword());
        place.setCrawlBatchId(batchId);
        place.setAutoGenerated(true);
        place.setLastCrawledAt(LocalDateTime.now());
        place.setCityCode(sysArea == null ? "" : sysArea.getCityCode());
        place.setUploadedBy(uploader);
        place.setIndoorAllowed(hasTag(candidate, "室内可进") || hasTag(candidate, "可进店"));
        place.setLargeDogFriendly(hasTag(candidate, "大狗友好"));
        place.setCatFriendly(hasTag(candidate, "猫咪友好"));
        place.setLeashRequired(hasTag(candidate, "需牵引"));
        place.setWaterAvailable(hasTag(candidate, "饮水"));
        place.setParkingAvailable(hasTag(candidate, "停车"));
        place.setFeeRequired(hasTag(candidate, "收费透明"));
        return place;
    }

    private boolean hasTag(CrawlCandidate candidate, String tag) {
        return candidate.tags().contains(tag);
    }

    private List<CrawlPlan> crawlPlans() {
        return List.of(
                new CrawlPlan("宠物友好餐厅", PlaceType.RESTAURANT),
                new CrawlPlan("可带宠物餐厅", PlaceType.RESTAURANT),
                new CrawlPlan("狗狗友好餐厅", PlaceType.RESTAURANT),
                new CrawlPlan("猫咪友好餐厅", PlaceType.RESTAURANT),
                new CrawlPlan("可带狗餐厅", PlaceType.RESTAURANT),
                new CrawlPlan("带宠物吃饭", PlaceType.RESTAURANT),
                new CrawlPlan("宠物友好咖啡", PlaceType.RESTAURANT),
                new CrawlPlan("宠物友好咖啡馆", PlaceType.RESTAURANT),
                new CrawlPlan("可带狗咖啡", PlaceType.RESTAURANT),
                new CrawlPlan("宠物友好茶馆", PlaceType.RESTAURANT),
                new CrawlPlan("宠物友好酒吧", PlaceType.RESTAURANT),
                new CrawlPlan("露台餐厅 宠物", PlaceType.RESTAURANT),
                new CrawlPlan("外摆餐厅 宠物", PlaceType.RESTAURANT),
                new CrawlPlan("宠物友好商场", PlaceType.MALL),
                new CrawlPlan("可带宠物商场", PlaceType.MALL),
                new CrawlPlan("宠物友好购物中心", PlaceType.MALL),
                new CrawlPlan("可带宠物购物中心", PlaceType.MALL),
                new CrawlPlan("宠物友好奥特莱斯", PlaceType.MALL),
                new CrawlPlan("可带宠物奥特莱斯", PlaceType.MALL),
                new CrawlPlan("宠物友好商业街", PlaceType.MALL),
                new CrawlPlan("可带宠物商业街", PlaceType.MALL),
                new CrawlPlan("宠物友好步行街", PlaceType.MALL),
                new CrawlPlan("可带宠物步行街", PlaceType.MALL),
                new CrawlPlan("宠物友好街区", PlaceType.MALL),
                new CrawlPlan("可带宠物街区", PlaceType.MALL),
                new CrawlPlan("宠物友好市集", PlaceType.MALL),
                new CrawlPlan("可带宠物市集", PlaceType.MALL),
                new CrawlPlan("宠物友好酒店", PlaceType.HOTEL),
                new CrawlPlan("可携带宠物酒店", PlaceType.HOTEL),
                new CrawlPlan("允许携带宠物酒店", PlaceType.HOTEL),
                new CrawlPlan("可带狗酒店", PlaceType.HOTEL),
                new CrawlPlan("狗狗友好酒店", PlaceType.HOTEL),
                new CrawlPlan("猫咪友好酒店", PlaceType.HOTEL),
                new CrawlPlan("宠物友好民宿", PlaceType.HOTEL),
                new CrawlPlan("可带宠物民宿", PlaceType.HOTEL),
                new CrawlPlan("宠物友好客栈", PlaceType.HOTEL),
                new CrawlPlan("宠物友好露营地", PlaceType.HOTEL),
                new CrawlPlan("可带宠物营地", PlaceType.HOTEL),
                new CrawlPlan("宠物友好房车营地", PlaceType.HOTEL),
                new CrawlPlan("宠物店", PlaceType.PET_STORE),
                new CrawlPlan("宠物用品店", PlaceType.PET_STORE),
                new CrawlPlan("宠物生活馆", PlaceType.PET_STORE),
                new CrawlPlan("宠物服务中心", PlaceType.PET_STORE),
                new CrawlPlan("宠物超市", PlaceType.PET_STORE),
                new CrawlPlan("宠物食品店", PlaceType.PET_STORE),
                new CrawlPlan("宠物洗护", PlaceType.PET_STORE),
                new CrawlPlan("宠物洗澡", PlaceType.PET_STORE),
                new CrawlPlan("宠物美容", PlaceType.PET_STORE),
                new CrawlPlan("宠物寄养", PlaceType.PET_STORE),
                new CrawlPlan("宠物训练", PlaceType.PET_STORE),
                new CrawlPlan("犬舍", PlaceType.PET_STORE),
                new CrawlPlan("猫舍", PlaceType.PET_STORE),
                new CrawlPlan("宠物医院", PlaceType.HOSPITAL),
                new CrawlPlan("动物医院", PlaceType.HOSPITAL),
                new CrawlPlan("兽医诊所", PlaceType.HOSPITAL),
                new CrawlPlan("宠物诊所", PlaceType.HOSPITAL),
                new CrawlPlan("动物诊所", PlaceType.HOSPITAL),
                new CrawlPlan("宠物医疗", PlaceType.HOSPITAL),
                new CrawlPlan("宠物急诊", PlaceType.HOSPITAL),
                new CrawlPlan("24小时宠物医院", PlaceType.HOSPITAL),
                new CrawlPlan("宠物体检", PlaceType.HOSPITAL),
                new CrawlPlan("宠物疫苗", PlaceType.HOSPITAL),
                new CrawlPlan("宠物绝育", PlaceType.HOSPITAL),
                new CrawlPlan("宠物友好景点", PlaceType.PARK),
                new CrawlPlan("可带宠物景点", PlaceType.PARK),
                new CrawlPlan("可带狗景点", PlaceType.PARK),
                new CrawlPlan("狗狗友好景点", PlaceType.PARK),
                new CrawlPlan("宠物友好旅游景点", PlaceType.PARK),
                new CrawlPlan("可带宠物旅游景点", PlaceType.PARK),
                new CrawlPlan("宠物友好风景区", PlaceType.PARK),
                new CrawlPlan("可带宠物风景区", PlaceType.PARK),
                new CrawlPlan("宠物友好公园", PlaceType.PARK),
                new CrawlPlan("可带宠物公园", PlaceType.PARK),
                new CrawlPlan("遛狗公园", PlaceType.PARK),
                new CrawlPlan("狗狗公园", PlaceType.PARK),
                new CrawlPlan("宠物活动区", PlaceType.PARK),
                new CrawlPlan("宠物友好绿地", PlaceType.PARK),
                new CrawlPlan("遛狗绿地", PlaceType.PARK),
                new CrawlPlan("宠物友好草坪", PlaceType.PARK),
                new CrawlPlan("遛狗草坪", PlaceType.PARK),
                new CrawlPlan("开放草坪", PlaceType.PARK),
                new CrawlPlan("大草坪", PlaceType.PARK),
                new CrawlPlan("宠物友好森林公园", PlaceType.PARK),
                new CrawlPlan("森林公园", PlaceType.PARK),
                new CrawlPlan("宠物友好湿地公园", PlaceType.PARK),
                new CrawlPlan("湿地公园", PlaceType.PARK),
                new CrawlPlan("宠物友好滨江", PlaceType.PARK),
                new CrawlPlan("滨江绿道", PlaceType.PARK),
                new CrawlPlan("宠物友好绿道", PlaceType.PARK),
                new CrawlPlan("可带宠物绿道", PlaceType.PARK),
                new CrawlPlan("宠物友好步道", PlaceType.PARK),
                new CrawlPlan("可带狗步道", PlaceType.PARK),
                new CrawlPlan("宠物友好露营", PlaceType.PARK),
                new CrawlPlan("可带宠物露营", PlaceType.PARK),
                new CrawlPlan("可带宠物农场", PlaceType.PARK),
                new CrawlPlan("宠物友好庄园", PlaceType.PARK));
    }

    private PlaceType inferType(String rawText, PlaceType fallbackType) {
        String value = text(rawText).toLowerCase(Locale.ROOT);
        if (containsAny(value, "宠物医院", "动物医院", "兽医", "诊所", "医疗", "急诊", "疫苗", "绝育", "体检")) {
            return PlaceType.HOSPITAL;
        }
        if (containsAny(value, "宠物店", "用品", "生活馆", "服务中心", "宠物超市", "洗护", "洗澡", "美容", "寄养", "训练", "犬舍", "猫舍")) {
            return PlaceType.PET_STORE;
        }
        if (containsAny(value, "酒店", "民宿", "客栈", "度假", "宾馆", "旅馆", "营地", "房车", "住宿")) {
            return PlaceType.HOTEL;
        }
        if (containsAny(value, "商场", "购物中心", "奥特莱斯", "商业街", "步行街", "街区", "市集", "mall", "plaza")) {
            return PlaceType.MALL;
        }
        if (containsAny(value, "餐厅", "咖啡", "咖啡馆", "酒吧", "茶馆", "甜品", "面包", "火锅", "烧烤", "西餐", "饭店", "餐饮", "外摆", "露台")) {
            return PlaceType.RESTAURANT;
        }
        if (containsAny(value, "景点", "风景区", "公园", "绿地", "草坪", "森林", "湿地", "滨江", "绿道", "步道", "露营", "农场", "庄园", "游玩")) {
            return PlaceType.PARK;
        }
        return fallbackType == null ? PlaceType.PARK : fallbackType;
    }

    private List<String> tagsOf(PlaceType type, String rawText) {
        String value = text(rawText);
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        switch (type.categoryType()) {
            case RESTAURANT -> {
                tags.add("可进店");
                tags.add("饮水");
                tags.add("收费透明");
            }
            case MALL -> {
                tags.add("室内可进");
                tags.add("停车");
                tags.add("可进店");
            }
            case HOTEL -> {
                tags.add("可预约");
                tags.add("收费透明");
                tags.add("室内可进");
            }
            case PARK -> {
                tags.add("草坪大");
                tags.add("需牵引");
                tags.add("免费");
            }
            case PET_STORE -> {
                tags.add("室内可进");
                tags.add("饮水");
            }
            case HOSPITAL -> {
                tags.add("室内可进");
                tags.add("可预约");
            }
            default -> tags.add("宠物友好");
        }
        if (containsAny(value, "大狗", "大型犬", "中大型犬")) {
            tags.add("大狗友好");
        }
        if (containsAny(value, "猫", "猫咪")) {
            tags.add("猫咪友好");
        }
        if (containsAny(value, "安静", "静谧", "茶馆", "书店", "民宿")) {
            tags.add("环境安静");
        }
        if (containsAny(value, "停车", "车库", "停车场")) {
            tags.add("停车");
        }
        if (containsAny(value, "饮水", "水碗", "供水")) {
            tags.add("饮水");
        }
        if (containsAny(value, "室内", "进店", "店内", "商场", "购物中心")) {
            tags.add("室内可进");
        }
        if (containsAny(value, "草坪", "绿地", "公园", "农场", "庄园")) {
            tags.add("草坪大");
        }
        if (containsAny(value, "阴凉", "树荫", "森林", "湿地")) {
            tags.add("阴凉多");
        }
        if (containsAny(value, "夜间", "照明", "24小时")) {
            tags.add("夜间照明");
        }
        if (containsAny(value, "免费", "开放")) {
            tags.add("免费");
        }
        if (containsAny(value, "收费", "门票", "酒店", "民宿", "餐厅")) {
            tags.add("收费透明");
        }
        if (containsAny(value, "预约", "预订", "酒店", "医院", "美容", "洗护")) {
            tags.add("可预约");
        }
        if (containsAny(value, "牵引", "牵绳", " leash")) {
            tags.add("需牵引");
        }
        return List.copyOf(tags);
    }

    private String descriptionOf(PlaceType type, String poiType, String keyword) {
        String typeText = type.getDisplayName();
        String detail = text(poiType).isBlank() ? "暂无高德分类详情" : poiType;
        return "根据高德地图“" + keyword + "”搜索结果整理，归类为" + typeText + "。高德分类："
                + detail + "。建议出行前确认宠物入内规则、牵引要求、营业时间和收费情况。";
    }

    private boolean containsAny(String value, String... needles) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String joinAddress(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            String clean = text(part);
            if (clean.isBlank() || "[]".equals(clean)) {
                continue;
            }
            if (!builder.toString().contains(clean)) {
                builder.append(clean);
            }
        }
        return builder.toString();
    }

    private String sourceKey(String provider, String poiId) {
        return text(provider).toUpperCase(Locale.ROOT) + "::" + text(poiId);
    }

    private String nameAddressKey(String name, String address) {
        return text(name).toLowerCase(Locale.ROOT) + "::" + text(address).toLowerCase(Locale.ROOT);
    }

    private String readableMessage(RuntimeException exception) {
        String message = text(exception.getMessage());
        return message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private String encode(String value) {
        return UriUtils.encode(text(value), StandardCharsets.UTF_8);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(text(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    public record CrawlStatus(
            boolean running,
            String city,
            String batchId,
            int fetched,
            int inserted,
            int skipped,
            String message,
            LocalDateTime startedAt,
            LocalDateTime finishedAt) {

        public static CrawlStatus idle() {
            return new CrawlStatus(false, "", "", 0, 0, 0, "尚未开始采集。", null, null);
        }

        public static CrawlStatus running(String city, String batchId) {
            return new CrawlStatus(true, city, batchId, 0, 0, 0, "正在准备采集。", LocalDateTime.now(), null);
        }

        public static CrawlStatus failed(String city, String message) {
            return new CrawlStatus(false, city, "", 0, 0, 0, message, LocalDateTime.now(), LocalDateTime.now());
        }
    }

    private record CrawlPlan(String keyword, PlaceType typeHint) {
    }

    private record CrawlCandidate(
            String sourceProvider,
            String sourcePoiId,
            String sourceKeyword,
            String name,
            PlaceType type,
            String address,
            Double latitude,
            Double longitude,
            List<String> tags,
            String description) {
    }

    private record ExistingPlaces(Set<String> sourceKeys, Set<String> nameAddressKeys) {
        private void add(CrawlCandidate candidate) {
            if (!candidate.sourcePoiId().isBlank()) {
                sourceKeys.add(sourceKey(candidate.sourceProvider(), candidate.sourcePoiId()));
            }
            nameAddressKeys.add(nameAddressKey(candidate.name(), candidate.address()));
        }

        private static String sourceKey(String provider, String poiId) {
            return text(provider).toUpperCase(Locale.ROOT) + "::" + text(poiId);
        }

        private static String nameAddressKey(String name, String address) {
            return text(name).toLowerCase(Locale.ROOT) + "::" + text(address).toLowerCase(Locale.ROOT);
        }
    }
}
