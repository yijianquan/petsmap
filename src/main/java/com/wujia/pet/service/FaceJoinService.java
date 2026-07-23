package com.wujia.pet.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class FaceJoinService {
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final double MAX_DISTANCE_METERS = 30.0;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    public FaceJoinService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis; this.objectMapper = objectMapper;
    }

    public Session create(Long groupId, Long inviterId, double latitude, double longitude, boolean direct) {
        for (int attempt = 0; attempt < 30; attempt++) {
            String code = String.format("%04d", random.nextInt(10000));
            Session session = new Session(code, groupId, inviterId, latitude, longitude, direct, Instant.now().plus(TTL).toEpochMilli());
            try {
                Boolean stored = redis.opsForValue().setIfAbsent(key(code), objectMapper.writeValueAsString(session), TTL);
                if (Boolean.TRUE.equals(stored)) {
                    redis.opsForSet().add(participantKey(code), String.valueOf(inviterId));
                    redis.expire(participantKey(code), TTL);
                    return session;
                }
            } catch (Exception exception) { throw new IllegalStateException("无法创建面对面邀请。", exception); }
        }
        throw new IllegalStateException("面对面邀请码生成失败，请重试。");
    }

    public Session require(String code) {
        try {
            String value = redis.opsForValue().get(key(normalize(code)));
            if (value == null) throw new IllegalArgumentException("面对面邀请已过期或不存在。");
            return objectMapper.readValue(value, Session.class);
        } catch (IllegalArgumentException exception) { throw exception; }
        catch (Exception exception) { throw new IllegalStateException("无法读取面对面邀请。", exception); }
    }

    public Session enter(String code, Long userId, double latitude, double longitude) {
        Session session = require(code);
        if (distance(latitude, longitude, session.latitude(), session.longitude()) > MAX_DISTANCE_METERS) {
            throw new IllegalArgumentException("请靠近发起人后再加入，当前不在面对面范围内。");
        }
        redis.opsForSet().add(participantKey(session.code()), String.valueOf(userId));
        redis.expire(participantKey(session.code()), Duration.ofMillis(Math.max(1000, session.expiresAt() - System.currentTimeMillis())));
        return session;
    }

    public Set<Long> participants(String code) {
        Set<String> values = redis.opsForSet().members(participantKey(normalize(code)));
        if (values == null) return Set.of();
        Set<Long> result = new LinkedHashSet<>();
        values.forEach(value -> { try { result.add(Long.parseLong(value)); } catch (NumberFormatException ignored) {} });
        return result;
    }

    private String normalize(String code) {
        String value = code == null ? "" : code.trim();
        if (!value.matches("\\d{4}")) throw new IllegalArgumentException("请输入四位数字。");
        return value;
    }
    private String key(String code){return "walk:face:session:"+code;}
    private String participantKey(String code){return "walk:face:participants:"+code;}
    private double distance(double a1,double o1,double a2,double o2){double r=Math.PI/180,x=(a2-a1)*r,y=(o2-o1)*r,q=Math.sin(x/2)*Math.sin(x/2)+Math.cos(a1*r)*Math.cos(a2*r)*Math.sin(y/2)*Math.sin(y/2);return 6371000*2*Math.atan2(Math.sqrt(q),Math.sqrt(1-q));}
    public record Session(String code, Long groupId, Long inviterId, double latitude, double longitude, boolean direct, long expiresAt) {}
}
