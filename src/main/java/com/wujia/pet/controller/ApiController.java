package com.wujia.pet.controller;

import com.wujia.pet.repository.PetFriendlyPlaceRepository;
import com.wujia.pet.service.MapSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final PetFriendlyPlaceRepository placeRepository;
    private final MapSearchService mapSearchService;

    public ApiController(
            PetFriendlyPlaceRepository placeRepository,
            MapSearchService mapSearchService) {
        this.placeRepository = placeRepository;
        this.mapSearchService = mapSearchService;
    }

    @GetMapping("/places")
    public Object places() {
        return placeRepository.findAllByOrderByIdDesc();
    }

    @GetMapping("/places/search")
    public Object searchPlaces(@RequestParam(defaultValue = "") String q) {
        return placeRepository.findTop10ByNameContainingOrderByIdDesc(q.trim());
    }

    @GetMapping("/map/search")
    public Object searchMap(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String city) {
        return mapSearchService.search(q, city);
    }
}
