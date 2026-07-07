package com.wujia.pet.service;

import com.wujia.pet.entity.UserAccount;
import com.wujia.pet.repository.UserAccountRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserAccountRepository userAccountRepository;

    public CurrentUserService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    public UserAccount requireUser(Authentication authentication) {
        return userAccountRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("当前登录用户不存在"));
    }
}
