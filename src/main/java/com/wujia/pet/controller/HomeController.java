package com.wujia.pet.controller;

import com.wujia.pet.entity.Gender;
import com.wujia.pet.entity.Pet;
import com.wujia.pet.entity.PetCategory;
import com.wujia.pet.repository.PetCalendarEventRepository;
import com.wujia.pet.repository.PetRepository;
import com.wujia.pet.service.CurrentUserService;
import jakarta.validation.Valid;
import jakarta.transaction.Transactional;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
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
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class HomeController {

    private final PetRepository petRepository;
    private final PetCalendarEventRepository eventRepository;
    private final CurrentUserService currentUserService;
    private static final int AVATAR_MAX_SIZE = 512;
    private static final float AVATAR_QUALITY = 0.82f;

    public HomeController(
            PetRepository petRepository,
            PetCalendarEventRepository eventRepository,
            CurrentUserService currentUserService) {
        this.petRepository = petRepository;
        this.eventRepository = eventRepository;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/")
    public String home(Model model, Authentication authentication) {
        model.addAttribute("pets", petRepository.findByOwnerUsernameOrderByBirthdayDesc(authentication.getName()));
        model.addAttribute("pet", new Pet());
        model.addAttribute("categories", PetCategory.values());
        model.addAttribute("genders", Gender.values());
        return "home";
    }

    @PostMapping("/pets")
    public String createPet(
            @Valid @ModelAttribute("pet") Pet pet,
            BindingResult bindingResult,
            @RequestParam(value = "avatarFile", required = false) MultipartFile avatarFile,
            Model model,
            Authentication authentication) throws IOException {
        if (bindingResult.hasErrors()) {
            model.addAttribute("pets", petRepository.findByOwnerUsernameOrderByBirthdayDesc(authentication.getName()));
            model.addAttribute("categories", PetCategory.values());
            model.addAttribute("genders", Gender.values());
            return "home";
        }
        pet.setOwner(currentUserService.requireUser(authentication));
        applyAvatarFile(pet, avatarFile);
        petRepository.save(pet);
        return "redirect:/";
    }

    @PostMapping("/pets/{id}/edit")
    public String updatePet(
            @PathVariable Long id,
            @Valid @ModelAttribute("pet") Pet form,
            BindingResult bindingResult,
            @RequestParam(value = "avatarFile", required = false) MultipartFile avatarFile,
            Authentication authentication,
            Model model) throws IOException {
        if (bindingResult.hasErrors()) {
            model.addAttribute("pets", petRepository.findByOwnerUsernameOrderByBirthdayDesc(authentication.getName()));
            model.addAttribute("categories", PetCategory.values());
            model.addAttribute("genders", Gender.values());
            return "home";
        }

        Pet pet = requireOwnedPet(id, authentication);
        pet.setNickname(form.getNickname());
        pet.setCategory(form.getCategory());
        pet.setGender(form.getGender());
        pet.setBirthday(form.getBirthday());
        applyAvatarFile(pet, avatarFile);
        petRepository.save(pet);
        return "redirect:/";
    }

    @GetMapping("/pets/{id}/avatar")
    public ResponseEntity<byte[]> avatar(@PathVariable Long id, Authentication authentication) {
        Pet pet = requireOwnedPet(id, authentication);
        if (!pet.hasAvatarData()) {
            return ResponseEntity.notFound().build();
        }
        MediaType mediaType = MediaType.parseMediaType(
                pet.getAvatarContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : pet.getAvatarContentType());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .contentType(mediaType)
                .body(pet.getAvatarData());
    }

    @Transactional
    @PostMapping("/pets/{id}/delete")
    public String deletePet(@PathVariable Long id, Authentication authentication) {
        Pet pet = requireOwnedPet(id, authentication);
        eventRepository.deleteByPetId(pet.getId());
        petRepository.delete(pet);
        return "redirect:/";
    }

    private Pet requireOwnedPet(Long id, Authentication authentication) {
        Pet pet = petRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("宠物不存在"));
        if (!pet.getOwner().getUsername().equals(authentication.getName())) {
            throw new IllegalArgumentException("只能操作自己的宠物");
        }
        return pet;
    }

    private void applyAvatarFile(Pet pet, MultipartFile avatarFile) throws IOException {
        if (avatarFile == null || avatarFile.isEmpty()) {
            return;
        }
        String contentType = avatarFile.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("头像必须是图片文件");
        }
        pet.setAvatarContentType(MediaType.IMAGE_JPEG_VALUE);
        pet.setAvatarData(compressAvatar(avatarFile));
        pet.setAvatarUrl(null);
    }

    private byte[] compressAvatar(MultipartFile avatarFile) throws IOException {
        BufferedImage source = ImageIO.read(avatarFile.getInputStream());
        if (source == null) {
            throw new IllegalArgumentException("无法识别头像图片格式，请上传 JPG 或 PNG 图片");
        }

        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        double scale = Math.min(1.0, (double) AVATAR_MAX_SIZE / Math.max(sourceWidth, sourceHeight));
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

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IllegalStateException("当前 JDK 不支持 JPG 图片压缩");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(AVATAR_QUALITY);
            }
            writer.write(null, new IIOImage(target, null, null), param);
            return output.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
