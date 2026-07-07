package com.wujia.pet.repository;

import com.wujia.pet.entity.PetFriendlyPlace;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PetFriendlyPlaceRepository extends JpaRepository<PetFriendlyPlace, Long> {

    List<PetFriendlyPlace> findAllByOrderByIdDesc();

    List<PetFriendlyPlace> findTop10ByNameContainingOrderByIdDesc(String name);

    boolean existsByName(String name);

    Optional<PetFriendlyPlace> findByName(String name);
}
