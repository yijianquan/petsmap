package com.wujia.pet.repository;

import com.wujia.pet.entity.SysArea;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysAreaRepository extends JpaRepository<SysArea, Long> {

    Page<SysArea> findByNameContainingIgnoreCase(String name, Pageable pageable);

    @Query("""
            select area from SysArea area
            where lower(area.name) like lower(concat('%', :name, '%'))
              and (:areaType = '' or area.areaType = :areaType)
            """)
    Page<SysArea> searchAreas(
            @Param("name") String name,
            @Param("areaType") String areaType,
            Pageable pageable);

    List<SysArea> findByAreaTypeAndAreaStatusOrderByHotDescAreaSortAscNameAsc(String areaType, String areaStatus);

    List<SysArea> findByAreaTypeAndAreaStatusOrderByAreaSortAscNameAsc(String areaType, String areaStatus);

    List<SysArea> findByPidAndAreaTypeAndAreaStatusOrderByHotDescAreaSortAscNameAsc(Long pid, String areaType, String areaStatus);

    List<SysArea> findByAreaTypeInOrderByAreaTypeAscAreaSortAscNameAsc(List<String> areaTypes);

    List<SysArea> findByAreaTypeAndCityCodeNotOrderByHotDescAreaSortAscNameAsc(String areaType, String cityCode);

    Optional<SysArea> findByCityCode(String cityCode);

    Optional<SysArea> findByAdcode(Long adcode);

    long countByPid(Long pid);
}
