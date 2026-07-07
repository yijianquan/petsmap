package com.wujia.pet.repository;

import com.wujia.pet.entity.PlaceComment;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceCommentRepository extends JpaRepository<PlaceComment, Long> {

    List<PlaceComment> findByPlaceIdInOrderByCreatedAtDesc(Collection<Long> placeIds);

    void deleteByPlaceId(Long placeId);
}
