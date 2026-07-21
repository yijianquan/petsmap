package com.wujia.pet.repository;

import com.wujia.pet.entity.PlaceFavorite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface PlaceFavoriteRepository extends JpaRepository<PlaceFavorite, Long> {
    Optional<PlaceFavorite> findByUserIdAndPlaceId(Long userId, Long placeId);
    boolean existsByUserIdAndPlaceId(Long userId, Long placeId);
    List<PlaceFavorite> findByUserIdOrderByCreatedAtDesc(Long userId);
    @Transactional
    void deleteByPlaceId(Long placeId);
}
