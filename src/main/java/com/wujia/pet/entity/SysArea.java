package com.wujia.pet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "sys_area")
public class SysArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "bigint(20) unsigned")
    private Long id;

    @Column(nullable = false, columnDefinition = "bigint(20) unsigned default 0")
    private Long pid = 0L;

    @NotBlank
    @Column(nullable = false, length = 255)
    private String name = "";

    @Column(length = 255)
    private String letter = "";

    @NotNull
    @Column(nullable = false)
    private Long adcode = 0L;

    @Column(length = 255)
    private String location = "";

    @Column(name = "area_sort")
    private Long areaSort;

    @Column(name = "area_status", nullable = false, length = 1)
    private String areaStatus = "1";

    @Column(name = "area_type", nullable = false, length = 1)
    private String areaType = "2";

    @Column(nullable = false, length = 1)
    private String hot = "0";

    @Column(name = "city_code", nullable = false, length = 30)
    private String cityCode = "";

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPid() {
        return pid;
    }

    public void setPid(Long pid) {
        this.pid = pid == null ? 0L : pid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name.trim();
    }

    public String getLetter() {
        return letter;
    }

    public void setLetter(String letter) {
        this.letter = letter == null ? "" : letter.trim();
    }

    public Long getAdcode() {
        return adcode;
    }

    public void setAdcode(Long adcode) {
        this.adcode = adcode == null ? 0L : adcode;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location == null ? "" : location.trim();
    }

    public Long getAreaSort() {
        return areaSort;
    }

    public void setAreaSort(Long areaSort) {
        this.areaSort = areaSort;
    }

    public String getAreaStatus() {
        return areaStatus;
    }

    public void setAreaStatus(String areaStatus) {
        this.areaStatus = areaStatus == null || areaStatus.isBlank() ? "1" : areaStatus.trim();
    }

    public String getAreaType() {
        return areaType;
    }

    public void setAreaType(String areaType) {
        this.areaType = areaType == null || areaType.isBlank() ? "2" : areaType.trim();
    }

    public String getHot() {
        return hot;
    }

    public void setHot(String hot) {
        this.hot = hot == null || hot.isBlank() ? "0" : hot.trim();
    }

    public String getCityCode() {
        return cityCode;
    }

    public void setCityCode(String cityCode) {
        this.cityCode = cityCode == null ? "" : cityCode.trim();
    }

    public String getDisplayName() {
        return name;
    }

    public String getTypeName() {
        return switch (areaType) {
            case "0" -> "国家";
            case "1" -> "省";
            case "2" -> "城市";
            case "3" -> "区县";
            default -> "未知";
        };
    }

    public String getStatusName() {
        return "1".equals(areaStatus) ? "生效" : "未生效";
    }

    public String getHotName() {
        return "1".equals(hot) ? "热门" : "非热门";
    }
}
