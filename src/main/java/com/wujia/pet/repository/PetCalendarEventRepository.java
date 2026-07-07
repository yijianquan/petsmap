package com.wujia.pet.repository;

import com.wujia.pet.entity.PetCalendarEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PetCalendarEventRepository extends JpaRepository<PetCalendarEvent, Long> {

    List<PetCalendarEvent> findByPetOwnerUsernameOrderByEventDateAsc(String username);

    void deleteByPetId(Long petId);
}
