package com.wujia.pet.controller;

import com.wujia.pet.entity.PetFriendlyPlace;
import com.wujia.pet.entity.PlaceComment;
import com.wujia.pet.entity.PlaceType;
import com.wujia.pet.entity.SysArea;
import com.wujia.pet.entity.UserAccount;
import com.wujia.pet.repository.PetFriendlyPlaceRepository;
import com.wujia.pet.repository.PlaceCommentRepository;
import com.wujia.pet.repository.SysAreaRepository;
import com.wujia.pet.repository.UserAccountRepository;
import com.wujia.pet.service.PlaceCrawlService;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final List<TagGroup> TAG_GROUPS = List.of(
            new TagGroup("宠物友好", List.of("大狗友好", "猫咪友好", "无小孩", "环境安静")),
            new TagGroup("设施服务", List.of("停车", "饮水", "室内可进", "可进店", "草坪大", "阴凉多", "夜间照明")),
            new TagGroup("规则费用", List.of("免费", "收费透明", "需牵引", "可预约")));

    private final PetFriendlyPlaceRepository placeRepository;
    private final PlaceCommentRepository commentRepository;
    private final UserAccountRepository userRepository;
    private final SysAreaRepository sysAreaRepository;
    private final PlaceCrawlService placeCrawlService;

    public AdminController(
            PetFriendlyPlaceRepository placeRepository,
            PlaceCommentRepository commentRepository,
            UserAccountRepository userRepository,
            SysAreaRepository sysAreaRepository,
            PlaceCrawlService placeCrawlService) {
        this.placeRepository = placeRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.sysAreaRepository = sysAreaRepository;
        this.placeCrawlService = placeCrawlService;
    }

    @GetMapping
    public String dashboard(Model model) {
        long placeCount = placeRepository.count();
        long commentCount = commentRepository.count();
        long miniUserCount = miniUsers().size();
        PlaceCrawlService.CrawlStatus crawlStatus = placeCrawlService.status();
        model.addAttribute("placeCount", placeCount);
        model.addAttribute("commentCount", commentCount);
        model.addAttribute("miniUserCount", miniUserCount);
        model.addAttribute("crawlStatus", crawlStatus);
        model.addAttribute("cityAreas", cityAreas());
        model.addAttribute("provinceAreas", provinceAreas());
        model.addAttribute("citiesByProvince", citiesByProvince());
        model.addAttribute("areaNames", areaNames());
        model.addAttribute("activeNav", "dashboard");
        return "admin-dashboard";
    }

    @PostMapping("/crawl/places")
    @ResponseBody
    public PlaceCrawlService.CrawlStatus startPlaceCrawl(@RequestParam(defaultValue = "") String cityCode) {
        SysArea city = cityCode == null || cityCode.isBlank() ? null : sysAreaRepository.findByCityCode(cityCode).orElse(null);
        if (city == null) {
            return placeCrawlService.start(placeCrawlService.defaultCity(), adminUser());
        }
        return placeCrawlService.start(city, adminUser());
    }

    @GetMapping("/crawl/places/status")
    @ResponseBody
    public PlaceCrawlService.CrawlStatus placeCrawlStatus() {
        return placeCrawlService.status();
    }

    @GetMapping("/places")
    public String places(
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String cityCode,
            @RequestParam(defaultValue = "") String type,
            @RequestParam(defaultValue = "") String commentPlaceId,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        populatePlaces(model, new PetFriendlyPlace(), name, cityCode, type, commentPlaceId, page);
        return "admin-places";
    }

    @PostMapping("/places")
    public String createPlace(
            @Valid @ModelAttribute("place") PetFriendlyPlace place,
            BindingResult bindingResult,
            @RequestParam(defaultValue = "") String cityCode,
            @RequestParam(value = "tagValues", required = false) List<String> tagValues,
            Model model) {
        if (bindingResult.hasErrors()) {
            populatePlaces(model, place, "", cityCode, "", "", 0);
            model.addAttribute("errorMessage", "请至少填写地点名称。");
            return "admin-places";
        }
        applyTags(place, tagValues);
        place.setCityCode(cityCode);
        place.setUploadedBy(adminUser());
        placeRepository.save(place);
        return "redirect:/admin/places";
    }

    @PostMapping("/places/{id}/edit")
    public String updatePlace(
            @PathVariable Long id,
            @Valid @ModelAttribute("place") PetFriendlyPlace form,
            BindingResult bindingResult,
            @RequestParam(defaultValue = "") String cityCode,
            @RequestParam(value = "tagValues", required = false) List<String> tagValues,
            Model model) {
        if (bindingResult.hasErrors()) {
            populatePlaces(model, form, "", cityCode, "", "", 0);
            model.addAttribute("errorMessage", "请至少填写地点名称。");
            return "admin-places";
        }
        PetFriendlyPlace place = placeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("地点不存在。"));
        applyTags(form, tagValues);
        form.setCityCode(cityCode);
        copyPlace(form, place);
        placeRepository.save(place);
        return "redirect:/admin/places";
    }

    @Transactional
    @PostMapping("/places/{id}/delete")
    public String deletePlace(@PathVariable Long id) {
        PetFriendlyPlace place = placeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("地点不存在。"));
        commentRepository.deleteByPlaceId(place.getId());
        placeRepository.delete(place);
        return "redirect:/admin/places";
    }

    @PostMapping("/comments/{id}/edit")
    public String updateComment(
            @PathVariable Long id,
            @RequestParam int rating,
            @RequestParam String content,
            @RequestParam(defaultValue = "") String returnName,
            @RequestParam(defaultValue = "") String returnCityCode,
            @RequestParam(defaultValue = "") String returnType,
            @RequestParam(defaultValue = "0") int returnPage) {
        PlaceComment comment = commentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("评论不存在。"));
        comment.setRating(rating);
        comment.setContent(content == null ? "" : content.trim());
        commentRepository.save(comment);
        return placeCommentRedirect(comment.getPlace().getId(), returnName, returnCityCode, returnType, returnPage);
    }

    @PostMapping("/places/{id}/comments")
    public String createComment(
            @PathVariable Long id,
            @RequestParam int rating,
            @RequestParam String content,
            @RequestParam(defaultValue = "") String returnName,
            @RequestParam(defaultValue = "") String returnCityCode,
            @RequestParam(defaultValue = "") String returnType,
            @RequestParam(defaultValue = "0") int returnPage) {
        PetFriendlyPlace place = placeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("地点不存在。"));
        PlaceComment comment = new PlaceComment();
        comment.setPlace(place);
        comment.setUser(adminUser());
        comment.setRating(rating);
        comment.setContent(content == null ? "" : content.trim());
        comment.setCreatedAt(LocalDateTime.now());
        commentRepository.save(comment);
        return placeCommentRedirect(place.getId(), returnName, returnCityCode, returnType, returnPage);
    }

    @PostMapping("/comments/{id}/delete")
    public String deleteComment(
            @PathVariable Long id,
            @RequestParam(defaultValue = "") String returnName,
            @RequestParam(defaultValue = "") String returnCityCode,
            @RequestParam(defaultValue = "") String returnType,
            @RequestParam(defaultValue = "0") int returnPage) {
        Long placeId = commentRepository.findById(id)
                .map(comment -> comment.getPlace().getId())
                .orElse(null);
        commentRepository.deleteById(id);
        return placeId == null
                ? "redirect:/admin/places"
                : placeCommentRedirect(placeId, returnName, returnCityCode, returnType, returnPage);
    }

    @GetMapping("/users")
    public String users(
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        Page<UserAccount> users = userRepository.findByRoleNot(
                "ROLE_ADMIN",
                PageRequest.of(Math.max(page, 0), 10, Sort.by(Sort.Direction.DESC, "id")));
        Map<String, Long> commentCounts = commentRepository.findAll().stream()
                .filter(comment -> comment.getUser() != null)
                .collect(Collectors.groupingBy(comment -> comment.getUser().getUsername(), Collectors.counting()));
        Map<String, Long> placeCounts = placeRepository.findAll().stream()
                .filter(place -> place.getUploadedBy() != null)
                .collect(Collectors.groupingBy(place -> place.getUploadedBy().getUsername(), Collectors.counting()));
        model.addAttribute("users", users);
        model.addAttribute("currentPage", users.getNumber());
        model.addAttribute("totalPages", users.getTotalPages());
        model.addAttribute("commentCounts", commentCounts);
        model.addAttribute("placeCounts", placeCounts);
        model.addAttribute("activeNav", "users");
        return "admin-users";
    }

    @GetMapping("/areas")
    public String areas(
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String areaType,
            @RequestParam(defaultValue = "0") int page,
            Model model) {
        String cleanName = name == null ? "" : name.trim();
        String cleanAreaType = areaType == null ? "" : areaType.trim();
        Page<SysArea> areas = sysAreaRepository.searchAreas(
                cleanName,
                cleanAreaType,
                PageRequest.of(Math.max(page, 0), 12, Sort.by(Sort.Direction.ASC, "areaSort", "id")));
        model.addAttribute("areas", areas);
        model.addAttribute("currentPage", areas.getNumber());
        model.addAttribute("totalPages", areas.getTotalPages());
        model.addAttribute("name", cleanName);
        model.addAttribute("areaType", cleanAreaType);
        model.addAttribute("area", new SysArea());
        model.addAttribute("parentAreas", parentAreas());
        model.addAttribute("areaNames", areaNames());
        model.addAttribute("activeNav", "areas");
        return "admin-areas";
    }

    @PostMapping("/areas")
    public String createArea(@Valid @ModelAttribute("area") SysArea area) {
        if (!canUseCityCode(area, null)) {
            return "redirect:/admin/areas";
        }
        sysAreaRepository.save(area);
        return "redirect:/admin/areas";
    }

    @PostMapping("/areas/{id}/edit")
    public String updateArea(@PathVariable Long id, @Valid @ModelAttribute("area") SysArea form) {
        SysArea area = sysAreaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("地区不存在。"));
        if (!canUseCityCode(form, id)) {
            return "redirect:/admin/areas";
        }
        copyArea(form, area);
        sysAreaRepository.save(area);
        return "redirect:/admin/areas";
    }

    @PostMapping("/areas/{id}/delete")
    public String deleteArea(@PathVariable Long id) {
        sysAreaRepository.findById(id).ifPresent(area -> {
            long placeCount = area.getCityCode().isBlank() ? 0 : placeRepository.countByCityCode(area.getCityCode());
            if (placeCount == 0 && sysAreaRepository.countByPid(area.getAdcode()) == 0) {
                sysAreaRepository.deleteById(id);
            }
        });
        return "redirect:/admin/areas";
    }

    private void populatePlaces(
            Model model,
            PetFriendlyPlace placeForm,
            String name,
            String cityCode,
            String type,
            String commentPlaceId,
            int page) {
        String cleanName = name == null ? "" : name.trim();
        String cleanCityCode = cityCode == null ? "" : cityCode.trim();
        String cleanType = type == null ? "" : type.trim();
        PlaceType selectedType = parsePlaceType(cleanType);
        Page<PetFriendlyPlace> placePage = placeRepository.searchAdminPlaces(
                cleanName,
                cleanCityCode,
                selectedType,
                PageRequest.of(Math.max(page, 0), 10, Sort.by(Sort.Direction.DESC, "id")));
        List<PetFriendlyPlace> places = placePage.getContent();
        applyRatingSummary(places);
        Map<Long, List<PlaceComment>> commentsByPlaceId = loadCommentsByPlace(places);
        model.addAttribute("places", places);
        model.addAttribute("placePage", placePage);
        model.addAttribute("currentPage", placePage.getNumber());
        model.addAttribute("totalPages", placePage.getTotalPages());
        model.addAttribute("name", cleanName);
        model.addAttribute("cityCode", cleanCityCode);
        model.addAttribute("type", cleanType);
        model.addAttribute("commentPlaceId", commentPlaceId == null ? "" : commentPlaceId.trim());
        model.addAttribute("cityAreas", cityAreas());
        model.addAttribute("provinceAreas", provinceAreas());
        model.addAttribute("citiesByProvince", citiesByProvince());
        model.addAttribute("cityNameByCode", cityNameByCode());
        model.addAttribute("areaNames", areaNames());
        model.addAttribute("commentsByPlaceId", commentsByPlaceId);
        model.addAttribute("place", placeForm);
        model.addAttribute("placeTypes", PlaceType.visibleValues());
        model.addAttribute("tagGroups", TAG_GROUPS);
        model.addAttribute("activeNav", "places");
    }

    private PlaceType parsePlaceType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return PlaceType.valueOf(type);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String placeCommentRedirect(
            Long placeId,
            String name,
            String cityCode,
            String type,
            int page) {
        return "redirect:/admin/places?name=" + encodeQuery(name)
                + "&cityCode=" + encodeQuery(cityCode)
                + "&type=" + encodeQuery(type)
                + "&page=" + Math.max(page, 0)
                + "&commentPlaceId=" + placeId;
    }

    private String encodeQuery(String value) {
        return org.springframework.web.util.UriUtils.encode(value == null ? "" : value.trim(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private void applyTags(PetFriendlyPlace place, List<String> tags) {
        List<String> cleanTags = cleanTags(tags);
        place.setTags(String.join(",", cleanTags));
        place.setIndoorAllowed(cleanTags.contains("室内可进") || cleanTags.contains("可进店"));
        place.setLargeDogFriendly(cleanTags.contains("大狗友好"));
        place.setCatFriendly(cleanTags.contains("猫咪友好"));
        place.setLeashRequired(cleanTags.contains("需牵引"));
        place.setWaterAvailable(cleanTags.contains("饮水"));
        place.setParkingAvailable(cleanTags.contains("停车"));
        place.setFeeRequired(cleanTags.contains("收费透明"));
        place.setPolicyNote(null);
    }

    private List<String> cleanTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(tag -> !tag.isBlank())
                .distinct()
                .toList();
    }

    private void copyPlace(PetFriendlyPlace source, PetFriendlyPlace target) {
        target.setName(source.getName());
        target.setType(source.getType());
        target.setAddress(source.getAddress());
        target.setLatitude(source.getLatitude());
        target.setLongitude(source.getLongitude());
        target.setDescription(source.getDescription());
        target.setCityCode(source.getCityCode());
        target.setTags(source.getTags());
        target.setIndoorAllowed(source.isIndoorAllowed());
        target.setLargeDogFriendly(source.isLargeDogFriendly());
        target.setCatFriendly(source.isCatFriendly());
        target.setLeashRequired(source.isLeashRequired());
        target.setWaterAvailable(source.isWaterAvailable());
        target.setParkingAvailable(source.isParkingAvailable());
        target.setFeeRequired(source.isFeeRequired());
        target.setPolicyNote(source.getPolicyNote());
    }

    private UserAccount adminUser() {
        return userRepository.findByUsername("admin")
                .orElseThrow(() -> new IllegalArgumentException("后台管理员不存在。"));
    }

    private List<SysArea> cityAreas() {
        return sysAreaRepository.findByAreaTypeAndAreaStatusOrderByAreaSortAscNameAsc("2", "1");
    }

    private List<SysArea> provinceAreas() {
        return sysAreaRepository.findByAreaTypeAndAreaStatusOrderByAreaSortAscNameAsc("1", "1");
    }

    private Map<Long, List<SysArea>> citiesByProvince() {
        return cityAreas().stream().collect(Collectors.groupingBy(SysArea::getPid));
    }

    private Map<String, String> cityNameByCode() {
        return cityAreas().stream()
                .filter(area -> area.getCityCode() != null && !area.getCityCode().isBlank())
                .collect(Collectors.toMap(SysArea::getCityCode, SysArea::getName, (left, right) -> left));
    }

    private List<SysArea> parentAreas() {
        return sysAreaRepository.findByAreaTypeInOrderByAreaTypeAscAreaSortAscNameAsc(List.of("0", "1", "2"));
    }

    private Map<Long, String> areaNames() {
        return sysAreaRepository.findAll().stream()
                .collect(Collectors.toMap(SysArea::getAdcode, SysArea::getName, (left, right) -> left));
    }

    private void copyArea(SysArea source, SysArea target) {
        target.setPid(source.getPid());
        target.setName(source.getName());
        target.setLetter(source.getLetter());
        target.setAdcode(source.getAdcode());
        target.setLocation(source.getLocation());
        target.setAreaSort(source.getAreaSort());
        target.setAreaStatus(source.getAreaStatus());
        target.setAreaType(source.getAreaType());
        target.setHot(source.getHot());
        target.setCityCode(source.getCityCode());
    }

    private boolean canUseCityCode(SysArea area, Long currentId) {
        String cityCode = area.getCityCode();
        if (!"2".equals(area.getAreaType())) {
            return cityCode == null || cityCode.isBlank()
                    || sysAreaRepository.findByCityCode(cityCode)
                    .map(existing -> Objects.equals(existing.getId(), currentId))
                    .orElse(true);
        }
        if (cityCode == null || cityCode.isBlank()) {
            return false;
        }
        return sysAreaRepository.findByCityCode(cityCode)
                .map(existing -> Objects.equals(existing.getId(), currentId))
                .orElse(true);
    }

    private List<UserAccount> miniUsers() {
        return userRepository.findAll().stream()
                .filter(user -> !"ROLE_ADMIN".equals(user.getRole()))
                .toList();
    }

    private Map<Long, List<PlaceComment>> loadCommentsByPlace(List<PetFriendlyPlace> places) {
        if (places.isEmpty()) {
            return Map.of();
        }
        return commentRepository.findByPlaceIdInOrderByCreatedAtDesc(places.stream().map(PetFriendlyPlace::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(comment -> comment.getPlace().getId()));
    }

    private void applyRatingSummary(List<PetFriendlyPlace> places) {
        if (places.isEmpty()) {
            return;
        }
        Map<Long, List<PlaceComment>> commentsByPlaceId = commentRepository.findByPlaceIdInOrderByCreatedAtDesc(
                        places.stream().map(PetFriendlyPlace::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(comment -> comment.getPlace().getId()));
        for (PetFriendlyPlace place : places) {
            List<PlaceComment> comments = commentsByPlaceId.get(place.getId());
            if (CollectionUtils.isEmpty(comments)) {
                place.setCommentCount(0);
                place.setAverageRating(0);
                continue;
            }
            place.setCommentCount(comments.size());
            place.setAverageRating(comments.stream().mapToInt(PlaceComment::getRating).average().orElse(0));
        }
    }

    public record TagGroup(String name, List<String> tags) {
    }
}
