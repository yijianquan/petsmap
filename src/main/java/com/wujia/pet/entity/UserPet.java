package com.wujia.pet.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name="user_pet")
public class UserPet {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(optional=false, fetch=FetchType.LAZY) @JoinColumn(name="owner_id") @JsonIgnore private UserAccount owner;
    @Column(nullable=false,length=20) private String species;
    @Column(nullable=false,length=64) private String name;
    @Column(name="breed_code",nullable=false,length=40) private String breedCode;
    @Column(name="gender_code",nullable=false,length=20) private String genderCode;
    @Column(nullable=false) private Boolean neutered=false;
    @Column(name="birth_date") private LocalDate birthDate;
    @Lob @JsonIgnore private byte[] avatarData;
    @Column(name="avatar_content_type",length=64) private String avatarContentType;
    public Long getId(){return id;} public void setId(Long id){this.id=id;}
    public UserAccount getOwner(){return owner;} public void setOwner(UserAccount v){owner=v;}
    public String getSpecies(){return species;} public void setSpecies(String v){species=v;}
    public String getName(){return name;} public void setName(String v){name=v;}
    public String getBreedCode(){return breedCode;} public void setBreedCode(String v){breedCode=v;}
    public String getGenderCode(){return genderCode;} public void setGenderCode(String v){genderCode=v;}
    public Boolean getNeutered(){return neutered;} public void setNeutered(Boolean v){neutered=v;}
    public LocalDate getBirthDate(){return birthDate;} public void setBirthDate(LocalDate v){birthDate=v;}
    public byte[] getAvatarData(){return avatarData;} public void setAvatarData(byte[] v){avatarData=v;}
    public String getAvatarContentType(){return avatarContentType;} public void setAvatarContentType(String v){avatarContentType=v;}
    public boolean hasAvatarData(){return avatarData!=null&&avatarData.length>0;}
}
