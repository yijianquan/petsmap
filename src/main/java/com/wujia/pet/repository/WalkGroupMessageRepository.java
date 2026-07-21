package com.wujia.pet.repository;

import com.wujia.pet.entity.WalkGroupMessage;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalkGroupMessageRepository extends JpaRepository<WalkGroupMessage, Long> {
    List<WalkGroupMessage> findByGroupIdOrderByIdDesc(Long groupId, Pageable pageable);
    List<WalkGroupMessage> findByGroupIdAndIdGreaterThanOrderByIdAsc(Long groupId, Long id, Pageable pageable);
    void deleteByGroupId(Long groupId);
}
