package com.wujia.pet.controller;

import com.wujia.pet.entity.PlaceComment;
import com.wujia.pet.entity.PetFriendlyPlace;
import com.wujia.pet.entity.PlaceType;
import com.wujia.pet.entity.UserAccount;
import com.wujia.pet.repository.PetFriendlyPlaceRepository;
import com.wujia.pet.repository.PlaceCommentRepository;
import com.wujia.pet.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.transaction.Transactional;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class PlaceController {

    private final PetFriendlyPlaceRepository placeRepository;
    private final PlaceCommentRepository commentRepository;
    private final CurrentUserService currentUserService;
    private static final int COMMENT_IMAGE_MAX_SIZE = 900;
    private static final float COMMENT_IMAGE_QUALITY = 0.78f;
    private static final List<String> BASE_TAGS = List.of(
            "停车", "饮水", "室内可进", "外摆友好", "可预约", "免费", "收费透明", "电梯友好", "阴凉多", "夜间照明");
    private static final List<String> PET_FRIENDLY_TAGS = List.of(
            "大狗友好", "无小孩", "有围栏", "草坪大", "可下地", "需牵引", "拾便方便", "同伴多");
    private static final List<String> QUIET_TAGS = List.of(
            "猫咪友好", "环境安静", "可放猫包", "少狗", "低噪音", "可进店", "独立角落", "不拥挤");
    private static final List<String> OTHER_TAGS = List.of(
            "小宠友好", "环境安静", "可带笼", "人流少");

    public PlaceController(
            PetFriendlyPlaceRepository placeRepository,
            PlaceCommentRepository commentRepository,
            CurrentUserService currentUserService) {
        this.placeRepository = placeRepository;
        this.commentRepository = commentRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/places")
    public String places(Model model, Authentication authentication) {
        if (isAdmin(authentication)) {
            return "redirect:/admin/places";
        }
        List<PetFriendlyPlace> places = placeRepository.findAllByOrderByIdDesc();
        Map<Long, List<PlaceComment>> commentsByPlaceId = loadCommentsByPlace(places);
        applyRatingSummary(places, commentsByPlaceId);
        model.addAttribute("places", places);
        model.addAttribute("commentsByPlaceId", commentsByPlaceId);
        model.addAttribute("place", new PetFriendlyPlace());
        model.addAttribute("placeTypes", PlaceType.visibleValues());
        populateUserContext(model, authentication);
        return "places";
    }

    @PostMapping("/places")
    public String createPlace(
            @Valid @ModelAttribute("place") PetFriendlyPlace place,
            BindingResult bindingResult,
            Model model,
            Authentication authentication) {
        if (bindingResult.hasErrors()) {
            populatePlacesModel(model, authentication);
            model.addAttribute("placeTypes", PlaceType.visibleValues());
            model.addAttribute("errorMessage", "地点信息填写不完整，请检查名称等必填项。");
            return "places";
        }
        place.setUploadedBy(currentUserService.requireUser(authentication));
        placeRepository.save(place);
        return "redirect:/places";
    }

    @PostMapping("/places/{id}/edit")
    public String updatePlace(
            @PathVariable Long id,
            @Valid @ModelAttribute("place") PetFriendlyPlace form,
            BindingResult bindingResult,
            Model model,
            Authentication authentication) {
        if (bindingResult.hasErrors()) {
            populatePlacesModel(model, authentication);
            model.addAttribute("placeTypes", PlaceType.visibleValues());
            model.addAttribute("errorMessage", "地点信息填写不完整，请检查名称等必填项。");
            return "places";
        }

        PetFriendlyPlace place = requireEditablePlace(id, authentication);
        place.setName(form.getName());
        place.setType(form.getType());
        place.setAddress(form.getAddress());
        place.setLatitude(form.getLatitude());
        place.setLongitude(form.getLongitude());
        place.setDescription(form.getDescription());
        place.setTags(form.getTags());
        place.setIndoorAllowed(form.isIndoorAllowed());
        place.setLargeDogFriendly(form.isLargeDogFriendly());
        place.setCatFriendly(form.isCatFriendly());
        place.setLeashRequired(form.isLeashRequired());
        place.setWaterAvailable(form.isWaterAvailable());
        place.setParkingAvailable(form.isParkingAvailable());
        place.setFeeRequired(form.isFeeRequired());
        place.setPolicyNote(form.getPolicyNote());
        placeRepository.save(place);
        return "redirect:/places";
    }

    @PostMapping("/places/comments/{id}/edit")
    public String updateComment(
            @PathVariable Long id,
            @RequestParam int rating,
            @RequestParam String content,
            Authentication authentication) {
        PlaceComment comment = requireEditableComment(id, authentication);
        comment.setRating(rating);
        comment.setContent(content == null ? "" : content.trim());
        commentRepository.save(comment);
        return "redirect:/places";
    }

    @PostMapping("/places/{id}/comments")
    public String createComment(
            @PathVariable Long id,
            @RequestParam int rating,
            @RequestParam String content,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            Authentication authentication) throws IOException {
        PetFriendlyPlace place = placeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("地点不存在"));
        PlaceComment comment = new PlaceComment();
        comment.setPlace(place);
        comment.setUser(currentUserService.requireUser(authentication));
        comment.setRating(rating);
        comment.setContent(content == null ? "" : content.trim());
        comment.setCreatedAt(LocalDateTime.now());
        applyCommentImage(comment, imageFile);
        commentRepository.save(comment);
        return "redirect:/places";
    }

    @GetMapping("/places/comments/{id}/image")
    public ResponseEntity<byte[]> commentImage(@PathVariable Long id) {
        PlaceComment comment = commentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("评论不存在"));
        if (!comment.hasImage()) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType = MediaType.parseMediaType(
                comment.getImageContentType() == null ? MediaType.IMAGE_JPEG_VALUE : comment.getImageContentType());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(mediaType)
                .body(comment.getImageData());
    }

    @Transactional
    @PostMapping("/places/{id}/delete")
    public String deletePlace(@PathVariable Long id, Authentication authentication) {
        PetFriendlyPlace place = requireEditablePlace(id, authentication);
        commentRepository.deleteByPlaceId(place.getId());
        placeRepository.delete(place);
        return "redirect:/places";
    }

    private PetFriendlyPlace requireEditablePlace(Long id, Authentication authentication) {
        PetFriendlyPlace place = placeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("地点不存在"));
        UserAccount uploader = place.getUploadedBy();
        boolean owner = uploader != null && uploader.getUsername().equals(authentication.getName());
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        if (!owner && !admin) {
            throw new IllegalArgumentException("只能操作自己上传的地点");
        }
        return place;
    }

    private PlaceComment requireEditableComment(Long id, Authentication authentication) {
        PlaceComment comment = commentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("评论不存在"));
        boolean owner = comment.getUser() != null && comment.getUser().getUsername().equals(authentication.getName());
        boolean admin = isAdmin(authentication);
        if (!owner && !admin) {
            throw new IllegalArgumentException("只能编辑自己的评论");
        }
        return comment;
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    private Map<Long, List<PlaceComment>> loadCommentsByPlace(List<PetFriendlyPlace> places) {
        List<Long> placeIds = places.stream()
                .map(PetFriendlyPlace::getId)
                .toList();
        if (placeIds.isEmpty()) {
            return Map.of();
        }
        return commentRepository.findByPlaceIdInOrderByCreatedAtDesc(placeIds).stream()
                .collect(Collectors.groupingBy(comment -> comment.getPlace().getId()));
    }

    private void populatePlacesModel(Model model, Authentication authentication) {
        List<PetFriendlyPlace> places = placeRepository.findAllByOrderByIdDesc();
        Map<Long, List<PlaceComment>> commentsByPlaceId = loadCommentsByPlace(places);
        applyRatingSummary(places, commentsByPlaceId);
        model.addAttribute("places", places);
        model.addAttribute("commentsByPlaceId", commentsByPlaceId);
        populateUserContext(model, authentication);
    }

    private void populateUserContext(Model model, Authentication authentication) {
        String username = authentication == null ? "" : authentication.getName();
        List<String> recommendedTags = recommendedTagsForTravel();
        model.addAttribute("currentUsername", username);
        model.addAttribute("currentUserAdmin", isAdmin(authentication));
        model.addAttribute("recommendedTags", recommendedTags);
        model.addAttribute("placeTags", allTagsFor(recommendedTags));
    }

    private List<String> recommendedTagsForTravel() {
        java.util.LinkedHashSet<String> tags = new java.util.LinkedHashSet<>();
        tags.addAll(PET_FRIENDLY_TAGS);
        tags.addAll(QUIET_TAGS);
        tags.addAll(BASE_TAGS);
        return List.copyOf(tags);
    }

    private List<String> allTagsFor(List<String> recommendedTags) {
        java.util.LinkedHashSet<String> tags = new java.util.LinkedHashSet<>(recommendedTags);
        tags.addAll(BASE_TAGS);
        tags.addAll(PET_FRIENDLY_TAGS);
        tags.addAll(QUIET_TAGS);
        tags.addAll(OTHER_TAGS);
        return List.copyOf(tags);
    }

    private void applyRatingSummary(
            List<PetFriendlyPlace> places,
            Map<Long, List<PlaceComment>> commentsByPlaceId) {
        places.forEach(place -> {
            List<PlaceComment> comments = commentsByPlaceId.getOrDefault(place.getId(), List.of());
            place.setCommentCount(comments.size());
            place.setAverageRating(comments.stream()
                    .mapToInt(PlaceComment::getRating)
                    .average()
                    .orElse(0));
            if (CollectionUtils.isEmpty(comments)) {
                return;
            }
            comments.sort(Comparator.comparing(PlaceComment::getCreatedAt).reversed());
        });
    }

    private void applyCommentImage(PlaceComment comment, MultipartFile imageFile) throws IOException {
        if (imageFile == null || imageFile.isEmpty()) {
            return;
        }
        String contentType = imageFile.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("评论图片必须是图片文件");
        }
        comment.setImageContentType(MediaType.IMAGE_JPEG_VALUE);
        comment.setImageData(compressCommentImage(imageFile));
    }

    private byte[] compressCommentImage(MultipartFile imageFile) throws IOException {
        BufferedImage source = ImageIO.read(imageFile.getInputStream());
        if (source == null) {
            throw new IllegalArgumentException("无法识别评论图片格式，请上传 JPG 或 PNG 图片");
        }

        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        double scale = Math.min(1.0, (double) COMMENT_IMAGE_MAX_SIZE / Math.max(sourceWidth, sourceHeight));
        int targetWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceHeight * scale));

        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(COMMENT_IMAGE_QUALITY);
            }
            writer.write(null, new IIOImage(target, null, null), param);
            return output.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
