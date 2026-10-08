package com.team.blog.media.application;

import com.team.blog.account.application.MemberLockService;
import com.team.blog.media.domain.MediaReasonCode;
import com.team.blog.media.infra.ImageRepository;
import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.error.TooManyRequestsException;
import com.team.blog.shared.infra.redis.RedisGuard;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 1인 저장 공간·하루 장수 (003 T058·T059, FR-014~FR-016, research R8·R9).
 *
 * <ul>
 *   <li>용량: presign 트랜잭션 A 안에서 회원 행을 {@code FOR UPDATE}로 잠그고(같은 회원의 동시 요청 직렬화) "지금 사용량 + 이번
 *       원본·썸네일"이 한도를 넘으면 409.
 *   <li>하루 장수: 커밋 뒤 Redis {@code img:daily:{회원}:{yyyyMMdd}}(서비스 시간대 날짜)를 Lua로 올리고, 한도를 넘으면 되돌린 뒤
 *       429 {@code DAILY_UPLOAD_LIMIT}({@code Retry-After} = 다음 0시까지). 완료 확인이 실패해도 돌려주지
 *       않는다(FR-016). 1분 제한에 걸린 요청만 {@link #releaseDaily}로 되돌린다. Redis 장애면 통과(02 §2-1).
 * </ul>
 */
@Component
public class StorageQuotaService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> RESERVE =
            new DefaultRedisScript<>(
                    """
                    local count = redis.call('INCR', KEYS[1])
                    if count == 1 then
                      redis.call('EXPIRE', KEYS[1], ARGV[2])
                    end
                    if count > tonumber(ARGV[1]) then
                      redis.call('DECR', KEYS[1])
                      return {0, count - 1}
                    end
                    return {1, count}
                    """,
                    List.class);

    private static final Duration KEY_TTL = Duration.ofDays(2);

    private final ImageRepository images;
    private final MemberLockService memberLock;
    private final StringRedisTemplate redis;
    private final RedisGuard redisGuard;
    private final ImageProperties properties;
    private final ZoneId zone;

    public StorageQuotaService(
            ImageRepository images,
            MemberLockService memberLock,
            StringRedisTemplate redis,
            RedisGuard redisGuard,
            ImageProperties properties,
            ZoneId serviceZoneId) {
        this.images = images;
        this.memberLock = memberLock;
        this.redis = redis;
        this.redisGuard = redisGuard;
        this.properties = properties;
        this.zone = serviceZoneId;
    }

    /** 용량 확인 (presign 트랜잭션 A 안). 넘으면 409 {@code STORAGE_QUOTA_EXCEEDED}. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void checkQuota(long memberId, long addBytes) {
        memberLock.lockForUpdate(memberId);
        long used = images.sumUsageBytes(memberId);
        long quota = properties.quotaBytes();
        if (used + addBytes > quota) {
            throw new BusinessRuleException(
                    MediaReasonCode.STORAGE_QUOTA_EXCEEDED,
                    Map.of("usedBytes", used, "quotaBytes", quota));
        }
    }

    /** 하루 장수를 1 올린다 (트랜잭션 밖). 넘으면 429 {@code DAILY_UPLOAD_LIMIT}. */
    @SuppressWarnings("unchecked")
    public void reserveDaily(long memberId, Instant now) {
        String key = dailyKey(memberId, now);
        List<Long> result =
                redisGuard.callWrite(
                        () ->
                                (List<Long>)
                                        redis.execute(
                                                RESERVE,
                                                List.of(key),
                                                String.valueOf(properties.dailyLimit()),
                                                String.valueOf(KEY_TTL.toSeconds())),
                        () -> List.of(1L, -1L));
        if (result.get(0) == 0L) {
            throw new TooManyRequestsException(
                    MediaReasonCode.DAILY_UPLOAD_LIMIT, secondsUntilNextDay(now));
        }
    }

    /** 1분 제한에 걸린 요청의 하루 장수를 되돌린다. */
    public void releaseDaily(long memberId, Instant now) {
        String key = dailyKey(memberId, now);
        redisGuard.runWrite(() -> redis.opsForValue().decrement(key), () -> {});
    }

    /** 오늘 올린 장수. Redis 장애면 빈 값. */
    public Optional<Integer> todayCount(long memberId, Instant now) {
        String key = dailyKey(memberId, now);
        return redisGuard.call(
                () -> {
                    String v = redis.opsForValue().get(key);
                    return Optional.of(v == null ? 0 : Math.max(0, Integer.parseInt(v)));
                },
                Optional::empty);
    }

    /** 오늘 사용량. */
    public long usedBytes(long memberId) {
        return images.sumUsageBytes(memberId);
    }

    String dailyKey(long memberId, Instant now) {
        return "img:daily:" + memberId + ":" + LocalDate.ofInstant(now, zone).format(DAY);
    }

    long secondsUntilNextDay(Instant now) {
        Instant next = LocalDate.ofInstant(now, zone).plusDays(1).atStartOfDay(zone).toInstant();
        return Math.max(1, Duration.between(now, next).toSeconds());
    }
}
