package com.wujia.pet.repository;

import com.wujia.pet.entity.WalkGroupMember;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalkGroupMemberRepository extends JpaRepository<WalkGroupMember, Long> {
    long countByGroupId(Long groupId);
    boolean existsByGroupIdAndUserId(Long groupId, Long userId);
    Optional<WalkGroupMember> findByGroupIdAndUserId(Long groupId, Long userId);
    List<WalkGroupMember> findByGroupIdOrderByJoinedAtAsc(Long groupId);
    List<WalkGroupMember> findByUserIdOrderByJoinedAtDesc(Long userId);
    void deleteByGroupId(Long groupId);
}
