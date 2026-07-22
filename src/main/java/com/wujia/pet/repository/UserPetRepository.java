package com.wujia.pet.repository;
import com.wujia.pet.entity.UserPet;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserPetRepository extends JpaRepository<UserPet,Long>{
 List<UserPet> findByOwnerIdOrderByIdDesc(Long ownerId);
 long countByOwnerIdAndSpecies(Long ownerId,String species);
}
