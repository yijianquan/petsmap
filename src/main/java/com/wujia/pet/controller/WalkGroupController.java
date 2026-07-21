package com.wujia.pet.controller;

import com.wujia.pet.entity.*;
import com.wujia.pet.repository.*;
import com.wujia.pet.service.MiniAppTokenService;
import com.wujia.pet.service.MapSearchService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/miniapp/api")
public class WalkGroupController {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final WalkGroupRepository groupRepository;
    private final WalkGroupMemberRepository memberRepository;
    private final WalkGroupMessageRepository messageRepository;
    private final PetFriendlyPlaceRepository placeRepository;
    private final SysAreaRepository areaRepository;
    private final MiniAppTokenService tokenService;
    private final MapSearchService mapSearchService;

    public WalkGroupController(WalkGroupRepository groupRepository, WalkGroupMemberRepository memberRepository,
            WalkGroupMessageRepository messageRepository, PetFriendlyPlaceRepository placeRepository,
            SysAreaRepository areaRepository, MiniAppTokenService tokenService, MapSearchService mapSearchService) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.messageRepository = messageRepository;
        this.placeRepository = placeRepository;
        this.areaRepository = areaRepository;
        this.tokenService = tokenService;
        this.mapSearchService = mapSearchService;
    }

    @GetMapping("/walk-groups")
    public List<Map<String, Object>> groups(@RequestHeader(value = "X-Miniapp-Token", required = false) String token,
            @RequestParam(defaultValue = "") String cityCode, @RequestParam(defaultValue = "") String cityName,
            @RequestParam(defaultValue = "") String q) {
        String resolvedCode = resolveCityCode(cityCode, cityName);
        UserAccount user = tokenService.optionalUser(token).orElse(null);
        return groupRepository.findByCityCodeAndNameContainingIgnoreCase(resolvedCode, clean(q)).stream()
                .map(group -> groupDto(group, user))
                .sorted(Comparator.<Map<String, Object>>comparingLong(item -> ((Number) item.get("memberCount")).longValue()).reversed()
                        .thenComparing(item -> String.valueOf(item.get("name"))))
                .toList();
    }

    @GetMapping("/walk-groups/{id}")
    public Map<String, Object> group(@RequestHeader(value = "X-Miniapp-Token", required = false) String token, @PathVariable Long id) {
        return groupDto(requireGroup(id), tokenService.optionalUser(token).orElse(null));
    }

    @GetMapping("/places/{placeId}/walk-groups")
    public List<Map<String, Object>> placeGroups(@RequestHeader(value = "X-Miniapp-Token", required = false) String token,
            @PathVariable Long placeId) {
        UserAccount user = tokenService.optionalUser(token).orElse(null);
        return groupRepository.findByPlaceId(placeId).stream().map(group -> groupDto(group, user))
                .sorted(Comparator.<Map<String, Object>>comparingLong(item -> ((Number) item.get("memberCount")).longValue()).reversed())
                .toList();
    }

    @PostMapping("/places/{placeId}/walk-groups")
    @Transactional
    public Map<String, Object> create(@RequestHeader(value = "X-Miniapp-Token", required = false) String token,
            @PathVariable Long placeId, @RequestBody GroupRequest request) {
        UserAccount user = tokenService.requireUser(token);
        PetFriendlyPlace place = placeRepository.findById(placeId).orElseThrow(() -> new IllegalArgumentException("地点不存在。"));
        String name = validateName(request == null ? "" : request.name());
        if (groupRepository.existsByPlaceIdAndNameIgnoreCase(placeId, name)) {
            throw new IllegalArgumentException("该地点已有同名群聊。");
        }
        if (groupRepository.existsByPlaceIdAndOwnerId(placeId, user.getId())) {
            throw new IllegalArgumentException("一个人在同一地点只能创建一个群聊。");
        }
        String cityCode = clean(place.getCityCode());
        if (cityCode.isBlank() && place.getLatitude() != null && place.getLongitude() != null) {
            Map<String, Object> reverse = mapSearchService.reverse(place.getLatitude(), place.getLongitude());
            String cityName = clean(String.valueOf(reverse.getOrDefault("cityName", "")));
            cityCode = cityName.isBlank() ? "" : areaRepository
                    .findFirstByNameAndAreaTypeOrderByIdAsc(cityName, "2")
                    .map(SysArea::getCityCode)
                    .orElse("");
        }
        if (cityCode.isBlank()) throw new IllegalArgumentException("请先选择地点所在城市。");
        if (clean(place.getCityCode()).isBlank()) {
            place.setCityCode(cityCode);
            placeRepository.save(place);
        }
        WalkGroup group = new WalkGroup();
        group.setName(name); group.setPlace(place); group.setOwner(user); group.setCityCode(place.getCityCode()); group.setCreatedAt(LocalDateTime.now());
        groupRepository.save(group);
        WalkGroupMember member = new WalkGroupMember();
        member.setGroup(group); member.setUser(user); member.setJoinedAt(LocalDateTime.now());
        memberRepository.save(member);
        return groupDto(group, user);
    }

    @PostMapping("/walk-groups/{id}/join")
    @Transactional
    public Map<String, Object> join(@RequestHeader(value = "X-Miniapp-Token", required = false) String token, @PathVariable Long id) {
        UserAccount user = tokenService.requireUser(token);
        WalkGroup group = requireGroup(id);
        if (!memberRepository.existsByGroupIdAndUserId(id, user.getId())) {
            WalkGroupMember member = new WalkGroupMember(); member.setGroup(group); member.setUser(user); member.setJoinedAt(LocalDateTime.now());
            memberRepository.save(member);
        }
        return groupDto(group, user);
    }

    @DeleteMapping("/walk-groups/{id}/leave")
    @Transactional
    public Map<String, Object> leave(@RequestHeader(value = "X-Miniapp-Token", required = false) String token, @PathVariable Long id) {
        UserAccount user = tokenService.requireUser(token);
        WalkGroup group = requireGroup(id);
        if (Objects.equals(group.getOwner().getId(), user.getId())) throw new IllegalArgumentException("群主请先解散群聊，不能直接退出。");
        memberRepository.findByGroupIdAndUserId(id, user.getId()).ifPresent(memberRepository::delete);
        return Map.of("joined", false);
    }

    @PutMapping("/walk-groups/{id}")
    public Map<String, Object> rename(@RequestHeader(value = "X-Miniapp-Token", required = false) String token,
            @PathVariable Long id, @RequestBody GroupRequest request) {
        UserAccount user = tokenService.requireUser(token); WalkGroup group = requireOwner(id, user);
        String name = validateName(request == null ? "" : request.name());
        if (groupRepository.existsByPlaceIdAndNameIgnoreCaseAndIdNot(group.getPlace().getId(), name, id))
            throw new IllegalArgumentException("该地点已有同名群聊。");
        group.setName(name); groupRepository.save(group); return groupDto(group, user);
    }

    @DeleteMapping("/walk-groups/{id}")
    @Transactional
    public Map<String, Object> dissolve(@RequestHeader(value = "X-Miniapp-Token", required = false) String token, @PathVariable Long id) {
        UserAccount user = tokenService.requireUser(token); WalkGroup group = requireOwner(id, user);
        messageRepository.deleteByGroupId(id); memberRepository.deleteByGroupId(id); groupRepository.delete(group);
        return Map.of("success", true);
    }

    @GetMapping("/walk-groups/{id}/members")
    public List<Map<String, Object>> members(@RequestHeader(value = "X-Miniapp-Token", required = false) String token, @PathVariable Long id) {
        UserAccount user = tokenService.requireUser(token); requireMember(id, user); WalkGroup group = requireGroup(id);
        return memberRepository.findByGroupIdOrderByJoinedAtAsc(id).stream().map(member -> memberDto(member, group)).toList();
    }

    @GetMapping("/walk-groups/{id}/messages")
    public List<Map<String, Object>> messages(@RequestHeader(value = "X-Miniapp-Token", required = false) String token,
            @PathVariable Long id, @RequestParam(defaultValue = "0") Long afterId) {
        UserAccount user = tokenService.requireUser(token); requireMember(id, user);
        List<WalkGroupMessage> messages = afterId > 0
                ? messageRepository.findByGroupIdAndIdGreaterThanOrderByIdAsc(id, afterId, PageRequest.of(0, 100))
                : new ArrayList<>(messageRepository.findByGroupIdOrderByIdDesc(id, PageRequest.of(0, 100)));
        if (afterId <= 0) Collections.reverse(messages);
        return messages.stream().map(message -> messageDto(message, user)).toList();
    }

    @PostMapping("/walk-groups/{id}/messages")
    public Map<String, Object> send(@RequestHeader(value = "X-Miniapp-Token", required = false) String token,
            @PathVariable Long id, @RequestBody MessageRequest request) {
        UserAccount user = tokenService.requireUser(token); requireMember(id, user); WalkGroup group = requireGroup(id);
        String content = clean(request == null ? "" : request.content());
        if (content.isBlank() || content.length() > 500) throw new IllegalArgumentException("消息请控制在 1-500 个字符。");
        WalkGroupMessage message = new WalkGroupMessage(); message.setGroup(group); message.setSender(user); message.setContent(content); message.setCreatedAt(LocalDateTime.now());
        return messageDto(messageRepository.save(message), user);
    }

    private WalkGroup requireGroup(Long id) { return groupRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("群聊不存在或已解散。")); }
    private void requireMember(Long id, UserAccount user) { if (!memberRepository.existsByGroupIdAndUserId(id, user.getId())) throw new IllegalArgumentException("请先加入群聊。"); }
    private WalkGroup requireOwner(Long id, UserAccount user) { WalkGroup group = requireGroup(id); if (!Objects.equals(group.getOwner().getId(), user.getId())) throw new IllegalArgumentException("只有群主可以执行该操作。"); return group; }
    private String validateName(String value) { String name = clean(value); if (name.length() < 2 || name.length() > 40) throw new IllegalArgumentException("群聊名称请控制在 2-40 个字符。"); return name; }
    private String resolveCityCode(String code, String name) {
        if (!clean(code).isBlank()) return clean(code);
        return areaRepository.findFirstByNameAndAreaTypeOrderByIdAsc(clean(name), "2")
                .map(SysArea::getCityCode)
                .orElse("");
    }

    private Map<String, Object> groupDto(WalkGroup group, UserAccount user) {
        Map<String, Object> dto = new LinkedHashMap<>(); long count = memberRepository.countByGroupId(group.getId());
        dto.put("id", group.getId()); dto.put("name", group.getName()); dto.put("cityCode", group.getCityCode());
        dto.put("placeId", group.getPlace().getId()); dto.put("placeName", group.getPlace().getName()); dto.put("placeAddress", group.getPlace().getAddress());
        dto.put("latitude", group.getPlace().getLatitude()); dto.put("longitude", group.getPlace().getLongitude());
        dto.put("memberCount", count); dto.put("ownerId", group.getOwner().getId()); dto.put("ownerName", displayName(group.getOwner()));
        dto.put("joined", user != null && memberRepository.existsByGroupIdAndUserId(group.getId(), user.getId()));
        dto.put("owner", user != null && Objects.equals(group.getOwner().getId(), user.getId()));
        return dto;
    }
    private Map<String, Object> memberDto(WalkGroupMember member, WalkGroup group) { UserAccount user = member.getUser(); return Map.of("id", user.getId(), "nickname", displayName(user), "avatarUrl", avatarUrl(user), "owner", Objects.equals(group.getOwner().getId(), user.getId())); }
    private Map<String, Object> messageDto(WalkGroupMessage message, UserAccount viewer) { UserAccount sender = message.getSender(); Map<String,Object> dto = new LinkedHashMap<>(); dto.put("id", message.getId()); dto.put("content", message.getContent()); dto.put("createdAt", message.getCreatedAt().format(TIME_FORMAT)); dto.put("senderId", sender.getId()); dto.put("senderName", displayName(sender)); dto.put("avatarUrl", avatarUrl(sender)); dto.put("mine", Objects.equals(sender.getId(), viewer.getId())); return dto; }
    private String avatarUrl(UserAccount user) { return user.hasAvatarData() ? "/miniapp/api/users/" + user.getId() + "/avatar" : clean(user.getAvatarUrl()); }
    private String displayName(UserAccount user) { return clean(user.getNickname()).isBlank() ? user.getUsername() : user.getNickname(); }
    private String clean(String value) { return value == null ? "" : value.trim(); }
    private record GroupRequest(String name, String cityCode) {}
    private record MessageRequest(String content) {}
}
