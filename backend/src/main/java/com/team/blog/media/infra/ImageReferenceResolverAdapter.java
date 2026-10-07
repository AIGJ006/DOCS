package com.team.blog.media.infra;

import com.team.blog.media.application.ImageUrlResolver;
import com.team.blog.shared.application.markdown.ImageReferenceResolver;
import com.team.blog.shared.application.markdown.OwnedImage;
import com.team.blog.shared.config.CoreProperties;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 사진 판별 임시 구현 (002 T025). 주소 → 키 규칙(23 §2-4)과 "누가 올렸나" 조회(23 §6-1)만 한다.
 *
 * <p>// TODO(003): 교체 — 003 이미지 업로드가 {@code ImageUrls.keyOf}·{@code ImageService}로 이 클래스를 대신한다.
 * 규칙(주소 앞부분 목록 + 키 모양, 업로더 확인 쿼리)은 그대로 옮긴다.
 */
@Component
public class ImageReferenceResolverAdapter implements ImageReferenceResolver {

    /** 저장 키 모양: {@code images/{yyyy}/{MM}/{uuid}(_thumb)?.{ext}} (04 §4-2, 23 §2-4). */
    static final Pattern STORAGE_KEY =
            Pattern.compile(
                    "images/\\d{4}/(0[1-9]|1[0-2])/"
                            + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                            + "(_thumb)?\\.(jpg|jpeg|png|gif|webp)");

    private final List<String> bases;
    private final NamedParameterJdbcTemplate jdbc;
    private final ImageUrlResolver urls;

    public ImageReferenceResolverAdapter(
            CoreProperties properties, NamedParameterJdbcTemplate jdbc, ImageUrlResolver urls) {
        List<String> all = new ArrayList<>();
        all.add(properties.image().publicBaseUrl());
        all.addAll(properties.image().legacyBaseUrls());
        this.bases = List.copyOf(all);
        this.jdbc = jdbc;
        this.urls = urls;
    }

    @Override
    public Optional<String> keyOf(String url) {
        return parseKey(url, bases);
    }

    /**
     * 주소 → 저장 키 (순수 함수, 테스트 스텁도 같은 규칙을 쓴다). 앞부분은 대소문자까지 정확히 같아야 하고, 쿼리·조각(#)이 붙은 주소는 우리 사진으로 보지
     * 않는다.
     */
    public static Optional<String> parseKey(String url, List<String> baseUrls) {
        if (url == null) {
            return Optional.empty();
        }
        for (String base : baseUrls) {
            String prefix = stripTrailingSlash(base.strip()) + "/";
            if (url.startsWith(prefix)) {
                String rest = url.substring(prefix.length());
                if (STORAGE_KEY.matcher(rest).matches()) {
                    return Optional.of(rest);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Map<String, OwnedImage> findOwned(Collection<String> keys, long ownerId) {
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }
        List<String> distinct = List.copyOf(new LinkedHashSet<>(keys));
        Map<String, OwnedImage> result = new LinkedHashMap<>();
        jdbc.query(
                "SELECT storage_key, thumb_storage_key FROM image"
                        + " WHERE storage_key IN (:keys) AND uploader_id = :ownerId",
                new MapSqlParameterSource().addValue("keys", distinct).addValue("ownerId", ownerId),
                rs -> {
                    String key = rs.getString("storage_key");
                    result.put(key, new OwnedImage(key, rs.getString("thumb_storage_key")));
                });
        return result;
    }

    @Override
    public String publicUrlOf(String storageKey) {
        return urls.publicUrl(storageKey);
    }

    private static String stripTrailingSlash(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '/') {
            end--;
        }
        return s.substring(0, end);
    }
}
