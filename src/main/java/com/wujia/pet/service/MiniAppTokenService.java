package com.wujia.pet.service;

import com.wujia.pet.entity.UserAccount;
import com.wujia.pet.repository.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MiniAppTokenService {

    private static final long TOKEN_TTL_SECONDS = 60L * 60 * 24 * 14;
    private static final String TOKEN_PREFIX = "v2";

    private final UserAccountRepository userAccountRepository;
    private final Map<String, SessionValue> sessions = new ConcurrentHashMap<>();

    @Value("${app.miniapp.token-secret:wu-jia-you-chong-miniapp-token}")
    private String tokenSecret;

    public MiniAppTokenService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    public String createToken(UserAccount user) {
        cleanupExpired();
        String username = user.getUsername();
        long expiresAt = Instant.now().plusSeconds(TOKEN_TTL_SECONDS).getEpochSecond();
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String payload = username + "." + expiresAt + "." + nonce;
        String signature = sign(payload);
        String token = TOKEN_PREFIX + "." + encode(payload) + "." + signature;
        sessions.put(token, new SessionValue(user.getUsername(), Instant.now().plusSeconds(TOKEN_TTL_SECONDS)));
        return token;
    }

    public UserAccount requireUser(String token) {
        return optionalUser(token).orElseThrow(() -> new IllegalArgumentException("请先登录。"));
    }

    public Optional<UserAccount> optionalUser(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        SessionValue session = sessions.get(token.trim());
        if (session != null && session.expiresAt().isAfter(Instant.now())) {
            return userAccountRepository.findByUsername(session.username());
        }
        if (session != null) {
            sessions.remove(token.trim());
        }
        return parseSignedToken(token.trim());
    }

    private void cleanupExpired() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private Optional<UserAccount> parseSignedToken(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !TOKEN_PREFIX.equals(parts[0])) {
            return Optional.empty();
        }
        String payload = decode(parts[1]);
        if (payload.isBlank() || !constantTimeEquals(sign(payload), parts[2])) {
            return Optional.empty();
        }
        String[] payloadParts = payload.split("\\.", 3);
        if (payloadParts.length != 3) {
            return Optional.empty();
        }
        long expiresAt;
        try {
            expiresAt = Long.parseLong(payloadParts[1]);
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
        if (Instant.ofEpochSecond(expiresAt).isBefore(Instant.now())) {
            return Optional.empty();
        }
        String username = payloadParts[0];
        Optional<UserAccount> user = userAccountRepository.findByUsername(username);
        user.ifPresent(value -> sessions.put(token, new SessionValue(username, Instant.ofEpochSecond(expiresAt))));
        return user;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(tokenSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成登录凭证。", exception);
        }
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        try {
            return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }

    private record SessionValue(String username, Instant expiresAt) {
    }
}
