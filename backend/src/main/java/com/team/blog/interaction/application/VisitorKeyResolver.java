package com.team.blog.interaction.application;

import com.team.blog.shared.security.Viewer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 방문자 키 (009 T030, research R5, FR-023, SC-010).
 *
 * <ul>
 *   <li>회원: {@code m:{회원 번호}} — 기기가 달라도 같은 사람
 *   <li>비회원 + 형식이 맞는 {@code vid} 쿠키(UUID): {@code v:{SHA-256("vid\n" + 소문자 UUID)}} — 쿠키 값 자체는 Redis
 *       키에도 남기지 않는다(구현 메모: data-model의 {@code v:{vid}}를 해시로 바꿈 — SC-010 시험이 vid 문자열도 0건을 요구)
 *   <li>비회원 + 쿠키 없음·형식 틀림: {@code h:{SHA-256(IP + "\n" + UA + "\n" + 오늘의 비밀값)}} — 비밀값이 하루 뒤 사라지면
 *       되돌릴 수 없다. 비밀값을 얻지 못하면(Redis 장애) 키가 없다(기록을 건너뜀)
 * </ul>
 *
 * 해시는 Base64url(패딩 없음). 하루 비밀값은 날짜별로 메모리에 두고, 날짜가 바뀌면 지난 값을 버린다. 결과 키는 로그에 남기지 않는다.
 */
@Component
public class VisitorKeyResolver {

    private static final Pattern UUID_FORMAT =
            Pattern.compile(
                    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final ViewSaltSource saltSource;
    private final Map<LocalDate, String> salts = new ConcurrentHashMap<>();

    public VisitorKeyResolver(ViewSaltSource saltSource) {
        this.saltSource = saltSource;
    }

    /** {@code vid} 쿠키 값이 UUID 형식인가 (아니면 없는 것으로 본다). */
    public static boolean isValidVid(String vid) {
        return vid != null && UUID_FORMAT.matcher(vid).matches();
    }

    /**
     * @param vid {@code vid} 쿠키 값 (없으면 {@code null})
     * @param clientIp {@code ClientIp.of(request)}
     * @param userAgent {@code User-Agent} (없으면 {@code null})
     * @param date 서비스 시간대의 오늘
     * @return 방문자 키. 쿠키 없는 비회원인데 비밀값을 얻지 못하면 빈 값
     */
    public Optional<String> resolve(
            Viewer viewer, String vid, String clientIp, String userAgent, LocalDate date) {
        if (viewer.isAuthenticated()) {
            return Optional.of("m:" + viewer.id());
        }
        if (isValidVid(vid)) {
            return Optional.of("v:" + sha256("vid\n" + vid.toLowerCase(Locale.ROOT)));
        }
        return saltOf(date)
                .map(
                        salt ->
                                "h:"
                                        + sha256(
                                                nullToEmpty(clientIp)
                                                        + "\n"
                                                        + nullToEmpty(userAgent)
                                                        + "\n"
                                                        + salt));
    }

    private Optional<String> saltOf(LocalDate date) {
        String cached = salts.get(date);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<String> salt = saltSource.salt(date);
        salt.ifPresent(
                value -> {
                    salts.keySet().removeIf(d -> !d.equals(date));
                    salts.put(date, value);
                });
        return salt;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String sha256(String text) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
