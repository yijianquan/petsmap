package com.wujia.pet.controller;

import com.wujia.pet.entity.PetFriendlyPlace;
import com.wujia.pet.entity.PlaceComment;
import com.wujia.pet.entity.PlaceFavorite;
import com.wujia.pet.entity.PlaceSourceType;
import com.wujia.pet.entity.PlaceType;
import com.wujia.pet.entity.SysArea;
import com.wujia.pet.entity.UserAccount;
import com.wujia.pet.repository.PetFriendlyPlaceRepository;
import com.wujia.pet.repository.PlaceCommentRepository;
import com.wujia.pet.repository.PlaceFavoriteRepository;
import com.wujia.pet.repository.SysAreaRepository;
import com.wujia.pet.repository.UserAccountRepository;
import com.wujia.pet.service.MapSearchService;
import com.wujia.pet.service.MiniAppTokenService;
import com.wujia.pet.service.PetAiService;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/miniapp/api")
public class MiniAppApiController {

    private static final DateTimeFormatter COMMENT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UserAccountRepository userRepository;
    private final PetFriendlyPlaceRepository placeRepository;
    private final PlaceCommentRepository commentRepository;
    private final PlaceFavoriteRepository favoriteRepository;
    private final SysAreaRepository sysAreaRepository;
    private final PasswordEncoder passwordEncoder;
    private final MiniAppTokenService tokenService;
    private final MapSearchService mapSearchService;
    private final PetAiService petAiService;

    public MiniAppApiController(
            UserAccountRepository userRepository,
            PetFriendlyPlaceRepository placeRepository,
            PlaceCommentRepository commentRepository,
            PlaceFavoriteRepository favoriteRepository,
            SysAreaRepository sysAreaRepository,
            PasswordEncoder passwordEncoder,
            MiniAppTokenService tokenService,
            MapSearchService mapSearchService,
            PetAiService petAiService) {
        this.userRepository = userRepository;
        this.placeRepository = placeRepository;
        this.commentRepository = commentRepository;
        this.favoriteRepository = favoriteRepository;
        this.sysAreaRepository = sysAreaRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.mapSearchService = mapSearchService;
        this.petAiService = petAiService;
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@RequestBody LoginRequest request) {
        String username = text(request.username());
        UserAccount user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码不正确。"));
        if (!passwordEncoder.matches(text(request.password()), user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码不正确。");
        }
        return Map.of("token", tokenService.createToken(user), "user", userDto(user));
    }

    @PostMapping("/auth/register")
    public Map<String, Object> register(@RequestBody LoginRequest request) {
        String username = text(request.username());
        String password = text(request.password());
        if (username.length() < 2 || username.length() > 32) {
            throw new IllegalArgumentException("用户名长度请控制在 2-32 个字符。");
        }
        if (password.length() < 6) {
            throw new IllegalArgumentException("密码至少 6 位。");
        }
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("用户名已存在。");
        }
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole("ROLE_USER");
        userRepository.save(user);
        return Map.of("token", tokenService.createToken(user), "user", userDto(user));
    }

    @PostMapping("/auth/wechat-dev")
    public Map<String, Object> wechatDevLogin(@RequestBody WechatLoginRequest request) {
        String nickname = text(request.nickname()).isBlank() ? randomNickname() : text(request.nickname());
        String avatarUrl = text(request.avatarUrl());
        String seed = nickname + "|" + avatarUrl;
        String username = "wxdev_" + Integer.toHexString(seed.hashCode()).replace("-", "0");
        UserAccount user = userRepository.findByUsername(username).orElseGet(() -> {
            UserAccount created = new UserAccount();
            created.setUsername(username);
            created.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
            created.setRole("ROLE_USER");
            created.setNickname(nickname);
            created.setAvatarUrl(avatarUrl);
            return userRepository.save(created);
        });
        if (text(user.getNickname()).isBlank()) {
            user.setNickname(nickname);
        }
        if (text(user.getAvatarUrl()).isBlank() && !avatarUrl.isBlank()) {
            user.setAvatarUrl(avatarUrl);
        }
        userRepository.save(user);
        return Map.of("token", tokenService.createToken(user), "user", userDto(user));
    }

    @GetMapping("/profile")
    public Map<String, Object> profile(@RequestHeader(value = "X-Miniapp-Token", required = false) String token) {
        UserAccount user = tokenService.requireUser(token);
        return Map.of("user", userDto(user));
    }

    @PutMapping("/profile")
    public Map<String, Object> updateProfile(
            @RequestHeader(value = "X-Miniapp-Token", required = false) String token,
            @RequestBody ProfileRequest request) {
        UserAccount user = tokenService.requireUser(token);
        applyProfile(user, request.nickname());
        user.setAvatarUrl(text(request.avatarUrl()));
        userRepository.save(user);
        return Map.of("user", userDto(user));
    }

    @PostMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> updateProfileAvatar(
            @RequestHeader(value = "X-Miniapp-Token", required = false) String token,
            @RequestParam String nickname,
            @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        UserAccount user = tokenService.requireUser(token);
        applyProfile(user, nickname);
        if (file != null && !file.isEmpty()) {
            String contentType = file.getContentType() == null ? MediaType.IMAGE_JPEG_VALUE : file.getContentType();
            if (!contentType.startsWith("image/")) {
                throw new IllegalArgumentException("头像必须是图片文件。");
            }
            user.setAvatarData(file.getBytes());
            user.setAvatarContentType(contentType);
            user.setAvatarUrl("");
        }
        userRepository.save(user);
        return Map.of("user", userDto(user));
    }

    @GetMapping("/users/{id}/avatar")
    public ResponseEntity<byte[]> userAvatar(@PathVariable Long id) {
        UserAccount user = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在。"));
        if (!user.hasAvatarData()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.parseMediaType(user.getAvatarContentType() == null ? MediaType.IMAGE_JPEG_VALUE : user.getAvatarContentType()))
                .body(user.getAvatarData());
    }

    @GetMapping("/options")
    public Map<String, Object> options() {
        return Map.of(
                "placeTypes", enumOptions(PlaceType.visibleValues()),
                "placeTags", List.of("大狗友好", "猫咪友好", "无小孩", "环境安静", "停车", "饮水", "室内可进", "可进店", "草坪大", "阴凉多", "夜间照明", "免费", "需牵引", "可预约"));
    }

    @GetMapping("/cities")
    public List<Map<String, Object>> cities() {
        Map<Long, String> provinceNames = sysAreaRepository.findByAreaTypeAndAreaStatusOrderByAreaSortAscNameAsc("1", "1").stream()
                .collect(Collectors.toMap(SysArea::getAdcode, SysArea::getName, (left, right) -> left));
        return sysAreaRepository.findByAreaTypeAndAreaStatusOrderByAreaSortAscNameAsc("2", "1").stream()
                .map(city -> cityDto(city, provinceNames))
                .toList();
    }

    @GetMapping("/places")
    public List<Map<String, Object>> places(
            @RequestHeader(value = "X-Miniapp-Token", required = false) String token,
            @RequestParam(required = false) Double minLat,
            @RequestParam(required = false) Double maxLat,
            @RequestParam(required = false) Double minLng,
            @RequestParam(required = false) Double maxLng) {
        UserAccount user = tokenService.optionalUser(token).orElse(null);
        List<PetFriendlyPlace> places = hasBounds(minLat, maxLat, minLng, maxLng)
                ? placeRepository.findInBounds(
                Math.min(minLat, maxLat),
                Math.max(minLat, maxLat),
                Math.min(minLng, maxLng),
                Math.max(minLng, maxLng),
                PageRequest.of(0, 180))
                : placeRepository.findAllByOrderByIdDesc();
        applyRatingSummary(places);
        return places.stream()
                .map(place -> placeDto(place, user))
                .toList();
    }

    @GetMapping("/places/mine")
    public List<Map<String, Object>> myPlaces(
            @RequestHeader(value = "X-Miniapp-Token", required = false) String token) {
        UserAccount user = tokenService.requireUser(token);
        List<PetFriendlyPlace> places = placeRepository.findByUploadedByUsernameOrderByIdDesc(user.getUsername());
        applyRatingSummary(places);
        return places.stream().map(place -> placeDto(place, user)).toList();
    }

    @GetMapping("/favorites")
    public List<Map<String, Object>> favorites(
            @RequestHeader(value = "X-Miniapp-Token", required = false) String token) {
        UserAccount user = tokenService.requireUser(token);
        List<PetFriendlyPlace> places = favoriteRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(PlaceFavorite::getPlace)
                .toList();
        applyRatingSummary(places);
        return places.stream().map(place -> placeDto(place, user)).toList();
    }

    @PostMapping("/places/{id}/favorite")
    public Map<String, Object> favorite(
            @RequestHeader(value = "X-Miniapp-Token", required = false) String token,
            @PathVariable Long id) {
        UserAccount user = tokenService.requireUser(token);
        PetFriendlyPlace place = placeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("地点不存在。"));
        if (!favoriteRepository.existsByUserIdAndPlaceId(user.getId(), id)) {
            PlaceFavorite favorite = new PlaceFavorite();
            favorite.setUser(user);
            favorite.setPlace(place);
            favoriteRepository.save(favorite);
        }
        return Map.of("favorited", true);
    }

    @DeleteMapping("/places/{id}/favorite")
    public Map<String, Object> unfavorite(
            @RequestHeader(value = "X-Miniapp-Token", required = false) String token,
            @PathVariable Long id) {
        UserAccount user = tokenService.requireUser(token);
        favoriteRepository.findByUserIdAndPlaceId(user.getId(), id).ifPresent(favoriteRepository::delete);
        return Map.of("favorited", false);
    }

    @GetMapping("/places/{id}")
    public Map<String, Object> placeDetail(
            @RequestHeader(value = "X-Miniapp-Token", required = false) String token,
            @PathVariable Long id) {
        UserAccount user = tokenService.optionalUser(token).orElse(null);
        PetFriendlyPlace place = placeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("地点不存在。"));
        applyRatingSummary(List.of(place));
        return placeDto(place, user);
    }

    @PostMapping("/places")
    public Map<String, Object> createPlace(
            @RequestHeader(value = "X-Miniapp-Token", required = false) String token,
            @RequestBody PlaceRequest request) {
        UserAccount user = tokenService.requireUser(token);
        if (request == null || text(request.cityCode()).isBlank()) {
            throw new IllegalArgumentException("无法确定地点所在城市，请重新选择地点。");
        }
        PetFriendlyPlace place = new PetFriendlyPlace();
        applyPlace(place, request);
        place.setUploadedBy(user);
        place.setPlaceSourceType(PlaceSourceType.USER_UPLOAD);
        place.setAutoGenerated(false);
        placeRepository.save(place);
        applyRatingSummary(List.of(place));
        return placeDto(place, user);
    }

    @PutMapping("/places/{id}")
    public Map<String, Object> updatePlace(
            @RequestHeader(value = "X-Miniapp-Token", required = false) String token,
            @PathVariable Long id,
            @RequestBody PlaceRequest request) {
        UserAccount user = tokenService.requireUser(token);
        PetFriendlyPlace place = requireEditablePlace(id, user);
        applyPlace(place, request);
        placeRepository.save(place);
        applyRatingSummary(List.of(place));
        return placeDto(place, user);
    }

    @DeleteMapping("/places/{id}")
    public Map<String, Object> deletePlace(
            @RequestHeader(value = "X-Miniapp-Token", required = false) String token,
            @PathVariable Long id) {
        UserAccount user = tokenService.requireUser(token);
        PetFriendlyPlace place = requireEditablePlace(id, user);
        favoriteRepository.deleteByPlaceId(place.getId());
        commentRepository.deleteByPlaceId(place.getId());
        placeRepository.delete(place);
        return Map.of("success", true);
    }

    @GetMapping("/places/{id}/comments")
    public List<Map<String, Object>> comments(
            @RequestHeader(value = "X-Miniapp-Token", required = false) String token,
            @PathVariable Long id) {
        UserAccount user = tokenService.optionalUser(token).orElse(null);
        return commentRepository.findByPlaceIdInOrderByCreatedAtDesc(List.of(id)).stream()
                .map(comment -> commentDto(comment, user))
                .toList();
    }

    @PostMapping(value = "/places/{id}/comments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> createComment(
            @RequestHeader(value = "X-Miniapp-Token", required = false) String token,
            @PathVariable Long id,
            @RequestParam int rating,
            @RequestParam String content,
            @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        UserAccount user = tokenService.requireUser(token);
        PetFriendlyPlace place = placeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("地点不存在。"));
        PlaceComment comment = new PlaceComment();
        comment.setPlace(place);
        comment.setUser(user);
        comment.setRating(rating);
        comment.setContent(text(content));
        comment.setCreatedAt(LocalDateTime.now());
        if (file != null && !file.isEmpty()) {
            comment.setImageContentType(file.getContentType() == null ? MediaType.IMAGE_JPEG_VALUE : file.getContentType());
            comment.setImageData(file.getBytes());
        }
        return commentDto(commentRepository.save(comment), user);
    }

    @PostMapping("/places/{id}/comments/json")
    public Map<String, Object> createCommentJson(
            @RequestHeader(value = "X-Miniapp-Token", required = false) String token,
            @PathVariable Long id,
            @RequestBody CommentRequest request) {
        UserAccount user = tokenService.requireUser(token);
        PetFriendlyPlace place = placeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("地点不存在。"));
        PlaceComment comment = new PlaceComment();
        comment.setPlace(place);
        comment.setUser(user);
        comment.setRating(request.rating());
        comment.setContent(required(request.content(), "请填写评论内容。"));
        comment.setCreatedAt(LocalDateTime.now());
        return commentDto(commentRepository.save(comment), user);
    }

    @GetMapping("/comments/{id}/image")
    public ResponseEntity<byte[]> commentImage(@PathVariable Long id) {
        PlaceComment comment = commentRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("评论不存在。"));
        if (!comment.hasImage()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(MediaType.parseMediaType(comment.getImageContentType() == null ? MediaType.IMAGE_JPEG_VALUE : comment.getImageContentType()))
                .body(comment.getImageData());
    }

    @GetMapping("/map/search")
    public Object mapSearch(
            @RequestHeader(value = "X-Miniapp-Token", required = false) String token,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String city) {
        return mapSearchService.search(q, city);
    }

    @GetMapping("/map/reverse")
    public Map<String, Object> mapReverse(
            @RequestParam Double latitude,
            @RequestParam Double longitude) {
        Map<String, Object> reverse = new LinkedHashMap<>(mapSearchService.reverse(latitude, longitude));
        String cityName = text(String.valueOf(reverse.getOrDefault("cityName", "")));
        reverse.put("cityCode", cityName.isBlank() ? "" : sysAreaRepository
                .findFirstByNameAndAreaTypeOrderByIdAsc(cityName, "2")
                .map(SysArea::getCityCode)
                .orElse(""));
        return reverse;
    }

    @PostMapping("/qa/ask")
    public Map<String, String> ask(
            @RequestHeader(value = "X-Miniapp-Token", required = false) String token,
            @RequestBody QuestionRequest request) {
        tokenService.requireUser(token);
        String question = request == null ? "" : text(request.question());
        if (question.isBlank()) {
            return Map.of("answer", "先输入一个宠物问题，我会尽量帮你梳理。");
        }
        return Map.of("answer", petAiService.answer(question));
    }

    private void applyPlace(PetFriendlyPlace place, PlaceRequest request) {
        place.setName(required(request.name(), "请填写地点名称。"));
        place.setType(parseEnum(PlaceType.class, request.type(), PlaceType.PARK).categoryType());
        place.setAddress(text(request.address()));
        place.setPhone(text(request.phone()));
        if (!text(request.cityCode()).isBlank()) {
            place.setCityCode(text(request.cityCode()));
        }
        place.setLatitude(request.latitude());
        place.setLongitude(request.longitude());
        place.setDescription(text(request.description()));
        place.setPolicyNote(text(request.policyNote()));
        place.setTags(String.join(",", request.tags() == null ? List.of() : request.tags()));
        place.setIndoorAllowed(request.indoorAllowed());
        place.setLargeDogFriendly(request.largeDogFriendly());
        place.setCatFriendly(request.catFriendly());
        place.setLeashRequired(request.leashRequired());
        place.setWaterAvailable(request.waterAvailable());
        place.setParkingAvailable(request.parkingAvailable());
        place.setFeeRequired(request.feeRequired());
    }

    private PetFriendlyPlace requireEditablePlace(Long id, UserAccount user) {
        PetFriendlyPlace place = placeRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("地点不存在。"));
        boolean owner = place.getUploadedBy() != null && Objects.equals(place.getUploadedBy().getUsername(), user.getUsername());
        boolean admin = "ROLE_ADMIN".equals(user.getRole());
        if (!owner && !admin) {
            throw new IllegalArgumentException("只能编辑或删除自己上传的地点。");
        }
        return place;
    }

    private void applyRatingSummary(List<PetFriendlyPlace> places) {
        if (CollectionUtils.isEmpty(places)) {
            return;
        }
        Map<Long, List<PlaceComment>> commentsByPlaceId = commentRepository.findByPlaceIdInOrderByCreatedAtDesc(
                        places.stream().map(PetFriendlyPlace::getId).toList())
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(comment -> comment.getPlace().getId()));
        for (PetFriendlyPlace place : places) {
            List<PlaceComment> comments = commentsByPlaceId.get(place.getId());
            if (CollectionUtils.isEmpty(comments)) {
                continue;
            }
            place.setCommentCount(comments.size());
            place.setAverageRating(comments.stream().mapToInt(PlaceComment::getRating).average().orElse(0));
        }
    }

    private Map<String, Object> userDto(UserAccount user) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", user.getId());
        dto.put("username", user.getUsername());
        dto.put("nickname", text(user.getNickname()).isBlank() ? user.getUsername() : user.getNickname());
        dto.put("avatarUrl", user.hasAvatarData() ? "/miniapp/api/users/" + user.getId() + "/avatar" : text(user.getAvatarUrl()));
        dto.put("role", user.getRole());
        return dto;
    }

    private Map<String, Object> cityDto(SysArea city, Map<Long, String> provinceNames) {
        Map<String, Object> dto = new LinkedHashMap<>();
        Double[] location = parseLocation(city.getLocation());
        dto.put("name", city.getName());
        dto.put("cityCode", city.getCityCode());
        dto.put("provinceName", provinceNames.getOrDefault(city.getPid(), ""));
        dto.put("letter", text(city.getLetter()).isBlank() ? "#" : text(city.getLetter()).substring(0, 1).toUpperCase());
        dto.put("latitude", location[0]);
        dto.put("longitude", location[1]);
        return dto;
    }

    private boolean hasBounds(Double minLat, Double maxLat, Double minLng, Double maxLng) {
        return minLat != null && maxLat != null && minLng != null && maxLng != null;
    }

    private Double[] parseLocation(String location) {
        String[] parts = text(location).split(",");
        if (parts.length != 2) {
            return new Double[] { null, null };
        }
        try {
            return new Double[] { Double.parseDouble(parts[1].trim()), Double.parseDouble(parts[0].trim()) };
        } catch (NumberFormatException exception) {
            return new Double[] { null, null };
        }
    }

    private Map<String, Object> placeDto(PetFriendlyPlace place, UserAccount user) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", place.getId());
        dto.put("name", place.getName());
        PlaceType categoryType = place.getType().categoryType();
        dto.put("type", categoryType);
        dto.put("typeName", categoryType.getDisplayName());
        dto.put("address", place.getAddress());
        dto.put("phone", place.getPhone());
        dto.put("latitude", place.getLatitude());
        dto.put("longitude", place.getLongitude());
        dto.put("description", place.getDescription());
        dto.put("policyNote", place.getPolicyNote());
        dto.put("tags", place.getTagList());
        dto.put("indoorAllowed", place.isIndoorAllowed());
        dto.put("largeDogFriendly", place.isLargeDogFriendly());
        dto.put("catFriendly", place.isCatFriendly());
        dto.put("leashRequired", place.isLeashRequired());
        dto.put("waterAvailable", place.isWaterAvailable());
        dto.put("parkingAvailable", place.isParkingAvailable());
        dto.put("feeRequired", place.isFeeRequired());
        dto.put("ratingText", place.getRatingText());
        dto.put("averageRating", place.getAverageRating());
        dto.put("commentCount", place.getCommentCount());
        dto.put("uploadedBy", place.getUploadedBy() == null ? "" : displayName(place.getUploadedBy()));
        dto.put("cityName", place.getCityCode() == null || place.getCityCode().isBlank()
                ? ""
                : sysAreaRepository.findByCityCode(place.getCityCode()).map(SysArea::getName).orElse(""));
        dto.put("cityCode", place.getCityCode());
        dto.put("placeSourceType", place.getPlaceSourceType());
        dto.put("placeSourceName", place.getPlaceSourceType().getDisplayName());
        dto.put("editable", user != null && (place.getUploadedBy() != null && Objects.equals(place.getUploadedBy().getUsername(), user.getUsername()) || "ROLE_ADMIN".equals(user.getRole())));
        dto.put("favorited", user != null && favoriteRepository.existsByUserIdAndPlaceId(user.getId(), place.getId()));
        return dto;
    }

    private Map<String, Object> commentDto(PlaceComment comment, UserAccount user) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", comment.getId());
        dto.put("placeId", comment.getPlace().getId());
        dto.put("username", displayName(comment.getUser()));
        dto.put("nickname", displayName(comment.getUser()));
        dto.put("rating", comment.getRating());
        dto.put("stars", comment.getStars());
        dto.put("content", comment.getContent());
        dto.put("createdAt", comment.getCreatedAt() == null ? "" : comment.getCreatedAt().format(COMMENT_TIME_FORMAT));
        dto.put("imageUrl", comment.hasImage() ? "/miniapp/api/comments/" + comment.getId() + "/image" : "");
        dto.put("editable", user != null && (Objects.equals(comment.getUser().getUsername(), user.getUsername()) || "ROLE_ADMIN".equals(user.getRole())));
        return dto;
    }

    private List<Map<String, String>> enumOptions(Object[] values) {
        return java.util.Arrays.stream(values)
                .map(value -> {
                    try {
                        Object displayName = value.getClass().getMethod("getDisplayName").invoke(value);
                        return Map.of("value", value.toString(), "label", String.valueOf(displayName));
                    } catch (ReflectiveOperationException exception) {
                        return Map.of("value", value.toString(), "label", value.toString());
                    }
                })
                .toList();
    }

    private <T extends Enum<T>> T parseEnum(Class<T> type, String value, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim());
        } catch (IllegalArgumentException exception) {
            return fallback;
        }
    }

    private String required(String value, String message) {
        String text = text(value);
        if (text.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    private void applyProfile(UserAccount user, String nicknameValue) {
        String nickname = text(nicknameValue);
        if (nickname.isBlank()) {
            throw new IllegalArgumentException("请填写昵称。");
        }
        if (nickname.length() > 32) {
            throw new IllegalArgumentException("昵称请控制在 32 个字符以内。");
        }
        user.setNickname(nickname);
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }

    private String randomNickname() {
        String letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder nickname = new StringBuilder();
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < 10; i += 1) {
            nickname.append(letters.charAt(random.nextInt(letters.length())));
        }
        return nickname.toString();
    }

    private String displayName(UserAccount user) {
        String nickname = text(user.getNickname());
        return nickname.isBlank() ? user.getUsername() : nickname;
    }

    private record LoginRequest(String username, String password) {
    }

    private record WechatLoginRequest(String code, String nickname, String avatarUrl) {
    }

    private record ProfileRequest(String nickname, String avatarUrl) {
    }

    private record PlaceRequest(
            String name,
            String type,
            String address,
            String phone,
            String cityCode,
            Double latitude,
            Double longitude,
            String description,
            String policyNote,
            List<String> tags,
            boolean indoorAllowed,
            boolean largeDogFriendly,
            boolean catFriendly,
            boolean leashRequired,
            boolean waterAvailable,
            boolean parkingAvailable,
            boolean feeRequired) {
    }

    private record QuestionRequest(String question) {
    }

    private record CommentRequest(int rating, String content) {
    }
}
