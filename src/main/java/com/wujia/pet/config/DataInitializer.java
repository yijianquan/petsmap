package com.wujia.pet.config;

import com.wujia.pet.entity.UserAccount;
import com.wujia.pet.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.enabled:true}")
    private boolean bootstrapEnabled;

    @Value("${app.bootstrap.admin-username:admin}")
    private String adminUsername;

    @Value("${app.bootstrap.admin-password:admin123}")
    private String adminPassword;

    public DataInitializer(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!bootstrapEnabled) {
            return;
        }

        userAccountRepository.findByUsername(adminUsername)
                .orElseGet(() -> {
                    UserAccount account = new UserAccount();
                    account.setUsername(adminUsername);
                    account.setPassword(passwordEncoder.encode(adminPassword));
                    account.setRole("ROLE_ADMIN");
                    return userAccountRepository.save(account);
                });
    }
}
