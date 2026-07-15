package com.wujia.pet.repository;

import com.wujia.pet.entity.PetFriendlyPlace;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PetFriendlyPlaceRepository extends JpaRepository<PetFriendlyPlace, Long> {

    List<PetFriendlyPlace> findAllByOrderByIdDesc();

    Page<PetFriendlyPlace> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("""
            select place from PetFriendlyPlace place
            where lower(place.name) like lower(concat('%', :name, '%'))
              and (:cityCode = '' or place.cityCode = :cityCode)
              and (:type is null or place.type = :type)
            """)
    Page<PetFriendlyPlace> searchAdminPlaces(
            @Param("name") String name,
            @Param("cityCode") String cityCode,
            @Param("type") com.wujia.pet.entity.PlaceType type,
            Pageable pageable);

    List<PetFriendlyPlace> findTop10ByNameContainingOrderByIdDesc(String name);

    long countByCityCode(String cityCode);

    boolean existsByName(String name);

    Optional<PetFriendlyPlace> findByName(String name);
}
