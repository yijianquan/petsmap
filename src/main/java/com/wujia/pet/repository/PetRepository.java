package com.wujia.pet.repository;

import com.wujia.pet.entity.Pet;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PetRepository extends JpaRepository<Pet, Long> {

    List<Pet> findByOwnerUsernameOrderByBirthdayDesc(String username);
}
