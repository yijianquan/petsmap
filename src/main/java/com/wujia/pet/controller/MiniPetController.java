package com.wujia.pet.controller;

import com.wujia.pet.entity.*;
import com.wujia.pet.repository.*;
import com.wujia.pet.service.MiniAppTokenService;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/miniapp/api")
public class MiniPetController {
    private final UserPetRepository pets; private final DictionaryItemRepository dictionaries; private final MiniAppTokenService tokens;
    public MiniPetController(UserPetRepository pets,DictionaryItemRepository dictionaries,MiniAppTokenService tokens){this.pets=pets;this.dictionaries=dictionaries;this.tokens=tokens;}

    @GetMapping("/dictionaries")
    public List<Map<String,Object>> dictionaries(@RequestParam String type,@RequestParam(defaultValue="") String parentCode){
        return dictionaries.findByDictTypeAndEnabledTrueOrderBySortOrderAscIdAsc(type).stream().filter(x->parentCode.isBlank()||parentCode.equals(x.getParentCode())).map(this::dictDto).toList();
    }
    @GetMapping("/pets") public List<Map<String,Object>> mine(@RequestHeader(value="X-Miniapp-Token",required=false) String token){return pets.findByOwnerIdOrderByIdDesc(tokens.requireUser(token).getId()).stream().map(this::petDto).toList();}
    @GetMapping("/pets/summary") public Map<String,Long> summary(@RequestHeader(value="X-Miniapp-Token",required=false) String token){Long id=tokens.requireUser(token).getId();return Map.of("cat",pets.countByOwnerIdAndSpecies(id,"CAT"),"dog",pets.countByOwnerIdAndSpecies(id,"DOG"),"other",pets.countByOwnerIdAndSpecies(id,"OTHER"));}

    @PostMapping(value="/pets",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> create(@RequestHeader(value="X-Miniapp-Token",required=false) String token,@RequestParam String species,@RequestParam String name,@RequestParam String breedCode,@RequestParam String genderCode,@RequestParam(defaultValue="false") boolean neutered,@RequestParam(required=false) String birthDate,@RequestParam(required=false) MultipartFile file) throws IOException{
        UserAccount owner=tokens.requireUser(token); validate(species,name,breedCode,genderCode);
        UserPet pet=new UserPet();pet.setOwner(owner);pet.setSpecies(species);pet.setName(name.trim());pet.setBreedCode(breedCode);pet.setGenderCode(genderCode);pet.setNeutered(neutered);if(birthDate!=null&&!birthDate.isBlank())pet.setBirthDate(LocalDate.parse(birthDate));applyImage(pet,file);return petDto(pets.save(pet));
    }
    @Transactional @DeleteMapping("/pets/{id}") public Map<String,Object> delete(@RequestHeader(value="X-Miniapp-Token",required=false) String token,@PathVariable Long id){UserAccount user=tokens.requireUser(token);UserPet pet=pets.findById(id).orElseThrow(()->new IllegalArgumentException("宠物不存在。"));if(!Objects.equals(pet.getOwner().getId(),user.getId()))throw new IllegalArgumentException("只能管理自己的宠物。");pets.delete(pet);return Map.of("success",true);}
    @GetMapping("/pets/{id}/avatar") public ResponseEntity<byte[]> avatar(@PathVariable Long id){UserPet pet=pets.findById(id).orElseThrow(()->new IllegalArgumentException("宠物不存在。"));if(!pet.hasAvatarData())return ResponseEntity.notFound().build();return ResponseEntity.ok().contentType(MediaType.parseMediaType(Optional.ofNullable(pet.getAvatarContentType()).orElse("image/jpeg"))).body(pet.getAvatarData());}

    private void validate(String species,String name,String breed,String gender){if(!List.of("CAT","DOG","OTHER").contains(species))throw new IllegalArgumentException("请选择宠物类型。");if(name==null||name.trim().isBlank()||name.trim().length()>64)throw new IllegalArgumentException("请填写宠物姓名。");DictionaryItem b=dictionaries.findByDictTypeAndItemCode("PET_BREED",breed).orElseThrow(()->new IllegalArgumentException("请选择有效品种。"));if(!species.equals(b.getParentCode())||!Boolean.TRUE.equals(b.getEnabled()))throw new IllegalArgumentException("品种与宠物类型不匹配。");DictionaryItem g=dictionaries.findByDictTypeAndItemCode("PET_GENDER",gender).orElseThrow(()->new IllegalArgumentException("请选择有效性别。"));if(!Boolean.TRUE.equals(g.getEnabled()))throw new IllegalArgumentException("请选择有效性别。");}
    private void applyImage(UserPet pet,MultipartFile file)throws IOException{if(file==null||file.isEmpty())return;if(file.getSize()>5*1024*1024)throw new IllegalArgumentException("宠物头像不能超过5MB。");pet.setAvatarData(file.getBytes());pet.setAvatarContentType(Optional.ofNullable(file.getContentType()).orElse("image/jpeg"));}
    private Map<String,Object> dictDto(DictionaryItem d){Map<String,Object> m=new LinkedHashMap<>();m.put("code",d.getItemCode());m.put("label",d.getLabel());m.put("parentCode",Optional.ofNullable(d.getParentCode()).orElse(""));return m;}
    public Map<String,Object> petDto(UserPet p){Map<String,Object> m=new LinkedHashMap<>();m.put("id",p.getId());m.put("species",p.getSpecies());m.put("name",p.getName());m.put("breedCode",p.getBreedCode());m.put("breedName",label("PET_BREED",p.getBreedCode()));m.put("genderCode",p.getGenderCode());m.put("genderName",label("PET_GENDER",p.getGenderCode()));m.put("neutered",p.getNeutered());m.put("birthDate",p.getBirthDate()==null?"":p.getBirthDate().toString());m.put("avatarUrl",p.hasAvatarData()?"/miniapp/api/pets/"+p.getId()+"/avatar":"");return m;}
    private String label(String type,String code){return dictionaries.findByDictTypeAndItemCode(type,code).map(DictionaryItem::getLabel).orElse(code);}
}
