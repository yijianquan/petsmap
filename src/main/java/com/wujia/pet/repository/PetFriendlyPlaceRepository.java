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

    List<PetFriendlyPlace> findByUploadedByUsernameOrderByIdDesc(String username);

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

    @Query("""
            select place from PetFriendlyPlace place
            where place.latitude is not null
              and place.longitude is not null
              and place.latitude between :minLat and :maxLat
              and place.longitude between :minLng and :maxLng
            order by place.id desc
            """)
    List<PetFriendlyPlace> findInBounds(
            @Param("minLat") Double minLat,
            @Param("maxLat") Double maxLat,
            @Param("minLng") Double minLng,
            @Param("maxLng") Double maxLng,
            Pageable pageable);

    List<PetFriendlyPlace> findTop10ByNameContainingOrderByIdDesc(String name);

    long countByCityCode(String cityCode);

    boolean existsByName(String name);

    Optional<PetFriendlyPlace> findByName(String name);

    Optional<PetFriendlyPlace> findBySourceProviderAndSourcePoiId(String sourceProvider, String sourcePoiId);

    Optional<PetFriendlyPlace> findFirstByNameAndAddress(String name, String address);
}
