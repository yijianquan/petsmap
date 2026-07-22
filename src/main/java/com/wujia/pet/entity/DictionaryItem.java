package com.wujia.pet.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "dictionary_item", uniqueConstraints = @UniqueConstraint(columnNames = {"dict_type", "item_code"}))
public class DictionaryItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name="dict_type", nullable=false, length=40) private String dictType;
    @Column(name="item_code", nullable=false, length=40) private String itemCode;
    @Column(nullable=false, length=80) private String label;
    @Column(name="parent_code", length=40) private String parentCode;
    @Column(name="sort_order", nullable=false) private Integer sortOrder = 0;
    @Column(nullable=false) private Boolean enabled = true;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public String getDictType(){return dictType;} public void setDictType(String v){dictType=v;}
    public String getItemCode(){return itemCode;} public void setItemCode(String v){itemCode=v;}
    public String getLabel(){return label;} public void setLabel(String v){label=v;}
    public String getParentCode(){return parentCode;} public void setParentCode(String v){parentCode=v;}
    public Integer getSortOrder(){return sortOrder;} public void setSortOrder(Integer v){sortOrder=v;}
    public Boolean getEnabled(){return enabled;} public void setEnabled(Boolean v){enabled=v;}
}
