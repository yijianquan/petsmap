package com.wujia.pet.controller;

import com.wujia.pet.entity.CalendarEventType;
import com.wujia.pet.entity.PetCalendarEvent;
import com.wujia.pet.repository.PetCalendarEventRepository;
import com.wujia.pet.repository.PetRepository;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class CalendarController {

    private final PetCalendarEventRepository eventRepository;
    private final PetRepository petRepository;

    public CalendarController(PetCalendarEventRepository eventRepository, PetRepository petRepository) {
        this.eventRepository = eventRepository;
        this.petRepository = petRepository;
    }

    @GetMapping("/calendar")
    public String calendar(Model model, Authentication authentication) {
        model.addAttribute("events", eventRepository.findByPetOwnerUsernameOrderByEventDateAsc(authentication.getName()));
        model.addAttribute("pets", petRepository.findByOwnerUsernameOrderByBirthdayDesc(authentication.getName()));
        model.addAttribute("event", new PetCalendarEvent());
        model.addAttribute("eventTypes", CalendarEventType.values());
        return "calendar";
    }

    @PostMapping("/calendar/events")
    public String createEvent(
            @Valid @ModelAttribute("event") PetCalendarEvent event,
            BindingResult bindingResult,
            @RequestParam Long petId,
            Model model,
            Authentication authentication) {
        var pets = petRepository.findByOwnerUsernameOrderByBirthdayDesc(authentication.getName());
        var pet = pets.stream()
                .filter(item -> item.getId().equals(petId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("请选择自己的宠物"));
        event.setPet(pet);

        if (bindingResult.hasErrors()) {
            model.addAttribute("events", eventRepository.findByPetOwnerUsernameOrderByEventDateAsc(authentication.getName()));
            model.addAttribute("pets", pets);
            model.addAttribute("eventTypes", CalendarEventType.values());
            return "calendar";
        }
        eventRepository.save(event);
        return "redirect:/calendar";
    }

    @PostMapping("/calendar/events/{id}/edit")
    public String updateEvent(
            @PathVariable Long id,
            @Valid @ModelAttribute("event") PetCalendarEvent form,
            BindingResult bindingResult,
            @RequestParam Long petId,
            Authentication authentication,
            Model model) {
        var pets = petRepository.findByOwnerUsernameOrderByBirthdayDesc(authentication.getName());
        var pet = pets.stream()
                .filter(item -> item.getId().equals(petId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("请选择自己的宠物"));

        if (bindingResult.hasErrors()) {
            model.addAttribute("events", eventRepository.findByPetOwnerUsernameOrderByEventDateAsc(authentication.getName()));
            model.addAttribute("pets", pets);
            model.addAttribute("eventTypes", CalendarEventType.values());
            return "calendar";
        }

        PetCalendarEvent event = requireOwnedEvent(id, authentication);
        event.setPet(pet);
        event.setType(form.getType());
        event.setVaccineCategory(form.getVaccineCategory());
        event.setEventDate(form.getEventDate());
        event.setNote(form.getNote());
        eventRepository.save(event);
        return "redirect:/calendar";
    }

    @PostMapping("/calendar/events/{id}/delete")
    public String deleteEvent(@PathVariable Long id, Authentication authentication) {
        PetCalendarEvent event = requireOwnedEvent(id, authentication);
        eventRepository.delete(event);
        return "redirect:/calendar";
    }

    private PetCalendarEvent requireOwnedEvent(Long id, Authentication authentication) {
        PetCalendarEvent event = eventRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("日历记录不存在"));
        if (!event.getPet().getOwner().getUsername().equals(authentication.getName())) {
            throw new IllegalArgumentException("只能操作自己的日历记录");
        }
        return event;
    }
}
