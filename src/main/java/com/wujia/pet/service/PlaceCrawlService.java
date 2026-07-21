package com.wujia.pet.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wujia.pet.entity.PetFriendlyPlace;
import com.wujia.pet.entity.PlaceSourceType;
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
    private static final int DEFAULT_MAX_PAGE = 5;
    private static final int PRIORITY_MAX_PAGE = 10;
    private static final long AMAP_REQUEST_INTERVAL_MS = 1_100L;
    private static final int QPS_RETRY_LIMIT = 4;
    private static final String PROVIDER = "AMAP";

    private final RestClient restClient;
    private final PetFriendlyPlaceRepository placeRepository;
    private final SysAreaRepository sysAreaRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object amapRateLock = new Object();

    private long nextAmapRequestAt;

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
            SearchScope cityScope = cityScope(city, sysArea);
            List<SearchScope> districtScopes = loadDistrictScopes(cityScope);
            for (CrawlPlan plan : crawlPlans()) {
                String planLabel = crawlPlanLabel(plan);
                List<SearchScope> scopes = text(plan.keyword()).isBlank() ? districtScopes : List.of(cityScope);
                for (SearchScope scope : scopes) {
                    updateProgress(city, batchId, fetched, inserted, skipped,
                            "正在采集：" + planLabel + " · " + scope.name());
                    for (int page = 1; page <= plan.maxPage(); page++) {
                        AmapSearchPage searchPage = searchAmap(scope, sysArea, plan, page);
                        if (searchPage.rawCount() == 0) {
                            break;
                        }
                        fetched += searchPage.rawCount();
                        for (CrawlCandidate candidate : searchPage.candidates()) {
                            if (isDuplicate(candidate, existing)) {
                                enrichExistingPhone(candidate);
                                skipped++;
                                continue;
                            }
                            PetFriendlyPlace place = toPlace(candidate, sysArea, batchId, uploader);
                            placeRepository.save(place);
                            existing.add(candidate);
                            inserted++;
                        }
                        updateProgress(city, batchId, fetched, inserted, skipped,
                                "正在采集：" + planLabel + " · " + scope.name());
                        if (searchPage.rawCount() < PAGE_SIZE) {
                            break;
                        }
                    }
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

    private void enrichExistingPhone(CrawlCandidate candidate) {
        if (candidate.phone().isBlank()) {
            return;
        }
        PetFriendlyPlace place = (!candidate.sourcePoiId().isBlank()
                ? placeRepository.findBySourceProviderAndSourcePoiId(candidate.sourceProvider(), candidate.sourcePoiId())
                : java.util.Optional.<PetFriendlyPlace>empty())
                .or(() -> placeRepository.findFirstByNameAndAddress(candidate.name(), candidate.address()))
                .orElse(null);
        if (place != null && text(place.getPhone()).isBlank()) {
            place.setPhone(candidate.phone());
            place.setLastCrawledAt(LocalDateTime.now());
            placeRepository.save(place);
        }
    }

    private SearchScope cityScope(String city, SysArea sysArea) {
        String code = sysArea != null && sysArea.getAdcode() != null && sysArea.getAdcode() > 0
                ? String.valueOf(sysArea.getAdcode())
                : city;
        return new SearchScope(code, city);
    }

    private AmapSearchPage searchAmap(
            SearchScope scope,
            SysArea sysArea,
            CrawlPlan plan,
            int page) {
        URI uri = URI.create("https://restapi.amap.com/v3/place/text"
                + "?keywords=" + encode(plan.keyword())
                + "&types=" + encode(plan.typeCodes())
                + "&city=" + encode(scope.code())
                + "&citylimit=true"
                + "&offset=" + PAGE_SIZE
                + "&page=" + page
                + "&extensions=base"
                + "&key=" + encode(amapKey));
        JsonNode root = requestAmapWithRetry(uri);
        if (root == null) {
            throw new IllegalStateException("高德接口未返回数据。");
        }
        if (!"1".equals(root.path("status").asText())) {
            throw new IllegalStateException("高德接口错误：" + root.path("info").asText("未知错误")
                    + "（" + root.path("infocode").asText("") + "）");
        }
        List<CrawlCandidate> results = new ArrayList<>();
        JsonNode pois = root.path("pois");
        int rawCount = pois.isArray() ? pois.size() : 0;
        for (JsonNode poi : pois) {
            if (!matchesTargetCity(poi, sysArea)) {
                continue;
            }
            CrawlCandidate candidate = candidateOf(poi, plan);
            if (candidate != null) {
                results.add(candidate);
            }
        }
        return new AmapSearchPage(results, rawCount);
    }

    private List<SearchScope> loadDistrictScopes(SearchScope cityScope) {
        try {
            URI uri = URI.create("https://restapi.amap.com/v3/config/district"
                    + "?keywords=" + encode(cityScope.code())
                    + "&subdistrict=1&extensions=base&key=" + encode(amapKey));
            JsonNode root = requestAmapWithRetry(uri);
            if (root == null || !"1".equals(root.path("status").asText())) {
                return List.of(cityScope);
            }
            LinkedHashSet<SearchScope> scopes = new LinkedHashSet<>();
            JsonNode districts = root.path("districts");
            if (districts.isArray() && !districts.isEmpty()) {
                for (JsonNode district : districts.get(0).path("districts")) {
                    String adcode = text(district.path("adcode").asText(""));
                    String name = text(district.path("name").asText(""));
                    if (!adcode.isBlank() && !name.isBlank()) {
                        scopes.add(new SearchScope(adcode, name));
                    }
                }
            }
            return scopes.isEmpty() ? List.of(cityScope) : List.copyOf(scopes);
        } catch (RuntimeException exception) {
            return List.of(cityScope);
        }
    }

    private JsonNode requestAmapWithRetry(URI uri) {
        for (int attempt = 0; attempt <= QPS_RETRY_LIMIT; attempt++) {
            throttleAmapRequest();
            JsonNode root = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (root == null || "1".equals(root.path("status").asText())) {
                return root;
            }
            String infoCode = root.path("infocode").asText("");
            if (!isRetryableAmapLimit(infoCode) || attempt == QPS_RETRY_LIMIT) {
                return root;
            }
            waitBeforeRetry((long) Math.pow(2, attempt + 1) * 1_000L);
        }
        return null;
    }

    private void throttleAmapRequest() {
        synchronized (amapRateLock) {
            long now = System.currentTimeMillis();
            long waitMillis = Math.max(0L, nextAmapRequestAt - now);
            if (waitMillis > 0) {
                waitBeforeRetry(waitMillis);
            }
            nextAmapRequestAt = System.currentTimeMillis() + AMAP_REQUEST_INTERVAL_MS;
        }
    }

    private boolean isRetryableAmapLimit(String infoCode) {
        return Set.of("10014", "10015", "10016", "10019", "10020", "10021", "10022", "10023")
                .contains(text(infoCode));
    }

    private void waitBeforeRetry(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("采集任务已停止。", exception);
        }
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
        String phone = normalizePhone(poi.path("tel").asText(""));
        String poiText = name + " " + poiType + " " + address;
        String typeCode = text(poi.path("typecode").asText(""));
        PlaceType inferredType = inferType(poiText, typeCode);
        if (!acceptCandidate(inferredType, plan.typeHint(), poiText, matchesPlanTypeCode(typeCode, plan.typeCodes()))) {
            return null;
        }
        PlaceType type = normalizeAcceptedType(inferredType, plan.typeHint(), poiText);
        List<String> tags = tagsOf(type, poiText + " " + plan.keyword());
        String description = descriptionOf(type, poiType, plan.keyword());
        return new CrawlCandidate(
                PROVIDER,
                text(poi.path("id").asText("")),
                text(plan.keyword()).isBlank() ? plan.typeHint().getDisplayName() + "分类" : plan.keyword(),
                name,
                type,
                address,
                phone,
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
        place.setPhone(candidate.phone());
        place.setLatitude(candidate.latitude());
        place.setLongitude(candidate.longitude());
        place.setTags(String.join(",", candidate.tags()));
        place.setDescription(candidate.description());
        place.setSourceProvider(candidate.sourceProvider());
        place.setSourcePoiId(candidate.sourcePoiId());
        place.setSourceKeyword(candidate.sourceKeyword());
        place.setCrawlBatchId(batchId);
        place.setAutoGenerated(true);
        place.setPlaceSourceType(PlaceSourceType.IMPORTED);
        place.setLastCrawledAt(LocalDateTime.now());
        place.setCityCode(sysArea == null ? "" : sysArea.getCityCode());
        place.setUploadedBy(uploader);
        place.setIndoorAllowed(hasTag(candidate, "室内可进") || hasTag(candidate, "可进店"));
        place.setLargeDogFriendly(hasTag(candidate, "大狗友好"));
        place.setCatFriendly(hasTag(candidate, "猫咪友好"));
        place.setLeashRequired(hasTag(candidate, "需牵引"));
        place.setWaterAvailable(hasTag(candidate, "饮水"));
        place.setParkingAvailable(hasTag(candidate, "停车"));
        place.setFeeRequired(false);
        return place;
    }

    private boolean hasTag(CrawlCandidate candidate, String tag) {
        return candidate.tags().contains(tag);
    }

    private String normalizePhone(String value) {
        String phone = text(value).replace(";", " / ");
        return phone.length() > 120 ? phone.substring(0, 120) : phone;
    }

    private List<CrawlPlan> crawlPlans() {
        List<CrawlPlan> plans = new ArrayList<>();
        addCategoryPlan(plans, PlaceType.HOSPITAL, "090700|090701|090702");
        addCategoryPlan(plans, PlaceType.PET_STORE, "061211");
        addPriorityPlans(plans, PlaceType.HOSPITAL, "090700|090701|090702",
                "宠物医院"/*, "动物医院", "兽医诊所", "宠物诊所", "动物诊所",
                "宠物急诊", "24小时宠物医院", "犬猫医院", "动物医疗中心", "异宠医院"*/);
        addPriorityPlans(plans, PlaceType.PET_STORE, "061211",
                "宠物店"/*, "宠物用品店", "宠物生活馆", "宠物服务中心", "宠物超市",
                "宠物洗护", "宠物美容", "宠物寄养", "宠物会所", "犬猫生活馆"*/);
//        addPlans(plans, PlaceType.RESTAURANT,
//                "宠物友好餐厅", "可带宠物餐厅");
//        addPlans(plans, PlaceType.HOTEL,
//                "宠物友好酒店", "宠物友好民宿", "宠物友好客栈", "可带宠物度假村", "宠物友好露营地", "可带宠物营地", "宠物友好房车营地",
//                "可带狗民宿", "宠物可入住", "携宠入住", "携宠酒店", "携宠民宿");
//        addPlans(plans, PlaceType.PARK,
//                "宠物友好景点",
//                "宠物友好公园", "可带宠物公园", "遛狗公园", "狗狗公园", "遛狗草坪",
//                "宠物友好森林公园", "宠物友好湿地公园");
        return List.copyOf(plans);
    }

    private void addPlans(List<CrawlPlan> plans, PlaceType type, String... keywords) {
        Set<String> existing = new HashSet<>();
        for (CrawlPlan plan : plans) {
            existing.add(plan.keyword() + "::" + plan.typeHint());
        }
        for (String keyword : keywords) {
            String clean = text(keyword);
            String key = clean + "::" + type;
            if (!clean.isBlank() && existing.add(key)) {
                plans.add(new CrawlPlan(clean, type, "", DEFAULT_MAX_PAGE));
            }
        }
    }

    private void addCategoryPlan(List<CrawlPlan> plans, PlaceType type, String typeCodes) {
        plans.add(new CrawlPlan("", type, typeCodes, PRIORITY_MAX_PAGE));
    }

    private String crawlPlanLabel(CrawlPlan plan) {
        return text(plan.keyword()).isBlank() ? plan.typeHint().getDisplayName() + "（高德分类）" : plan.keyword();
    }

    private void addPriorityPlans(List<CrawlPlan> plans, PlaceType type, String typeCodes, String... keywords) {
        for (String keyword : keywords) {
            plans.add(new CrawlPlan(keyword, type, typeCodes, DEFAULT_MAX_PAGE));
        }
    }

    private boolean acceptCandidate(
            PlaceType inferredType,
            PlaceType planType,
            String rawText,
            boolean trustedTypeCode) {
        if (inferredType == null || planType == null || inferredType.categoryType() == PlaceType.MALL) {
            return false;
        }
        if (!trustedTypeCode && !hasPrecisePetSignal(inferredType, rawText)) {
            return false;
        }
        if (inferredType.categoryType() == planType.categoryType()) {
            return true;
        }
        return planType.categoryType() == PlaceType.PARK
                && inferredType.categoryType() == PlaceType.PET_STORE
                && containsAny(rawText, "宠物公园", "宠物运动公园", "宠物乐园");
    }

    private boolean matchesPlanTypeCode(String typeCode, String configuredCodes) {
        String cleanTypeCode = text(typeCode);
        if (cleanTypeCode.isBlank()) {
            return false;
        }
        for (String configuredCode : text(configuredCodes).split("\\|")) {
            if (cleanTypeCode.equals(text(configuredCode))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPrecisePetSignal(PlaceType inferredType, String rawText) {
        String value = text(rawText).toLowerCase(Locale.ROOT);
        return switch (inferredType.categoryType()) {
            case HOSPITAL -> containsAny(value, "宠物", "动物医院", "兽医", "犬猫", "猫专科", "异宠");
            case PET_STORE -> containsAny(value, "宠物", "狗", "猫", "犬", "pet", "洗护", "寄养", "犬舍", "猫舍");
            case RESTAURANT, HOTEL, PARK -> containsAny(value,
                    "宠物", "狗狗", "猫咪", "携宠", "可带宠", "可带狗", "可带猫",
                    "狗咖", "猫咖", "犬友好", "猫友好", "pet", "遛狗");
            default -> false;
        };
    }

    private PlaceType normalizeAcceptedType(PlaceType inferredType, PlaceType planType, String rawText) {
        if (planType.categoryType() == PlaceType.PARK
                && inferredType.categoryType() == PlaceType.PET_STORE
                && containsAny(rawText, "宠物公园", "宠物运动公园", "宠物乐园")) {
            return PlaceType.PARK;
        }
        return inferredType.categoryType();
    }

    private PlaceType inferType(String rawText, String typeCode) {
        if (text(typeCode).startsWith("0907")) {
            return PlaceType.HOSPITAL;
        }
        if (text(typeCode).equals("061211")) {
            return PlaceType.PET_STORE;
        }
        String value = text(rawText).toLowerCase(Locale.ROOT);
        if (containsAny(value, "宠物医院", "动物医院", "兽医", "诊所", "医疗", "急诊", "疫苗", "绝育", "体检", "牙科", "眼科", "骨科", "皮肤病", "康复")) {
            return PlaceType.HOSPITAL;
        }
        if (containsAny(value, "宠物店", "用品", "生活馆", "服务中心", "宠物超市", "洗护", "洗澡", "美容", "寄养", "训练", "托管", "宠物摄影", "宠物影像中心", "宠物乐园", "宠物会所", "犬舍", "猫舍", "猫咖", "狗咖", "鲜食", "宠物烘焙")) {
            return PlaceType.PET_STORE;
        }
        if (containsAny(value, "酒店", "民宿", "客栈", "度假", "宾馆", "旅馆", "营地", "房车", "住宿", "入住", "携宠", "帐篷")) {
            return PlaceType.HOTEL;
        }
        if (containsAny(value, "餐厅", "咖啡", "咖啡馆", "酒吧", "茶馆", "甜品", "面包", "烘焙", "brunch", "简餐", "火锅", "烧烤", "西餐", "日料", "饭店", "餐饮", "外摆", "露台", "庭院")) {
            return PlaceType.RESTAURANT;
        }
        if (containsAny(value, "景点", "风景区", "公园", "动物园", "乐园", "绿地", "草坪", "森林", "湿地", "滨江", "河滨", "海滨", "江滩", "湖滨", "绿道", "步道", "栈道", "徒步", "登山", "露营", "农场", "庄园", "游玩")) {
            return PlaceType.PARK;
        }
        if (containsAny(value, "商场", "购物中心", "奥特莱斯", "商业街", "步行街", "街区", "市集", "夜市", "书店", "mall", "plaza")) {
            return PlaceType.MALL;
        }
        return null;
    }

    private List<String> tagsOf(PlaceType type, String rawText) {
        String value = text(rawText);
        LinkedHashSet<String> tags = new LinkedHashSet<>();
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
        if (containsAny(value, "草坪", "大草坪", "绿地", "农场", "庄园")) {
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
        if (containsAny(value, "预约", "预订")) {
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

    private record CrawlPlan(String keyword, PlaceType typeHint, String typeCodes, int maxPage) {
    }

    private record SearchScope(String code, String name) {
    }

    private record AmapSearchPage(List<CrawlCandidate> candidates, int rawCount) {
    }

    private record CrawlCandidate(
            String sourceProvider,
            String sourcePoiId,
            String sourceKeyword,
            String name,
            PlaceType type,
            String address,
            String phone,
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
