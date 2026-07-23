package com.wujia.pet.repository;

import com.wujia.pet.entity.WalkGroupJoinRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalkGroupJoinRequestRepository extends JpaRepository<WalkGroupJoinRequest, Long> {
    List<WalkGroupJoinRequest> findByGroupIdAndStatusOrderByCreatedAtAsc(Long groupId, String status);
    long countByGroupIdAndStatus(Long groupId, String status);
    Optional<WalkGroupJoinRequest> findFirstByGroupIdAndApplicantIdAndStatusOrderByCreatedAtDesc(Long groupId, Long applicantId, String status);
    void deleteByGroupId(Long groupId);
}
