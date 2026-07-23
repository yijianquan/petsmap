package com.wujia.pet.repository;

import com.wujia.pet.entity.WalkGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface WalkGroupRepository extends JpaRepository<WalkGroup, Long> {
    List<WalkGroup> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    List<WalkGroup> findByCityCodeAndNameContainingIgnoreCase(String cityCode, String name);
    List<WalkGroup> findByPlaceId(Long placeId);
    boolean existsByPlaceIdAndNameIgnoreCase(Long placeId, String name);
    boolean existsByPlaceIdAndNameIgnoreCaseAndIdNot(Long placeId, String name, Long id);
    boolean existsByPlaceIdAndOwnerId(Long placeId, Long ownerId);

    @Query("""
            select walkGroup from WalkGroup walkGroup
            where (:cityCode = '' or walkGroup.cityCode = :cityCode)
              and lower(walkGroup.name) like lower(concat('%', :groupName, '%'))
              and lower(walkGroup.place.name) like lower(concat('%', :placeName, '%'))
            """)
    Page<WalkGroup> searchAdmin(
            @Param("cityCode") String cityCode,
            @Param("groupName") String groupName,
            @Param("placeName") String placeName,
            Pageable pageable);
}
