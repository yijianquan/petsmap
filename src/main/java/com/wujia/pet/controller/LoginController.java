package com.wujia.pet.controller;

import com.wujia.pet.entity.UserAccount;
import com.wujia.pet.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public LoginController(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @PostMapping("/register")
    public String createAccount(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            Model model) {
        String cleanUsername = username == null ? "" : username.trim();
        if (cleanUsername.length() < 3 || cleanUsername.length() > 64) {
            model.addAttribute("error", "用户名长度需为 3-64 位");
            model.addAttribute("username", cleanUsername);
            return "register";
        }
        if (password == null || password.length() < 6) {
            model.addAttribute("error", "密码至少 6 位");
            model.addAttribute("username", cleanUsername);
            return "register";
        }
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "两次输入的密码不一致");
            model.addAttribute("username", cleanUsername);
            return "register";
        }
        if (userAccountRepository.existsByUsername(cleanUsername)) {
            model.addAttribute("error", "用户名已存在");
            model.addAttribute("username", cleanUsername);
            return "register";
        }

        UserAccount account = new UserAccount();
        account.setUsername(cleanUsername);
        account.setPassword(passwordEncoder.encode(password));
        account.setRole("ROLE_USER");
        userAccountRepository.save(account);
        return "redirect:/login?registered";
    }
}
