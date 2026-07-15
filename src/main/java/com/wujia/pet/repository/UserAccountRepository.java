package com.wujia.pet.repository;

import com.wujia.pet.entity.UserAccount;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsername(String username);

    Page<UserAccount> findByRoleNot(String role, Pageable pageable);

    boolean existsByUsername(String username);
}
