package com.team.blog.media.application;

import com.team.blog.shared.config.CoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 주소 ↔ 저장 키 판별 한 곳 (FR-024, research R10, 23 §2-4). 002 {@code
 * ImageReferenceResolverAdapter.parseKey}의 규칙을 그대로 옮겼다.
 *
 * <ul>
 *   <li>지금 공개 주소({@code blog.image.public-base-url}) 또는 옛 주소 목록({@code legacy-base-urls}) + {@code
 *       /} + 키 모양({@link #STORAGE_KEY})일 때만 우리 사진이다. 앞부분 끝의 {@code /}는 정리한다.
 *   <li>앞부분·키는 대소문자까지 정확히 같아야 한다. 쿼리·조각({@code ?}·{@code #})이 붙으면 우리 사진이 아니다.
 *   <li>썸네일 키({@code _thumb})도 같은 규칙이다.
 * </ul>
 *
 * 주소를 만들 때는 항상 지금 공개 주소를 쓴다({@link ImageUrlResolver#publicUrl}).
 */
@Component
public class ImageUrls {

    /** 저장 키 모양: {@code images/{yyyy}/{MM}/{uuid}(_thumb)?.{ext}} (04 §4-2, 23 §2-4). */
    public static final Pattern STORAGE_KEY =
            Pattern.compile(
                    "images/\\d{4}/(0[1-9]|1[0-2])/"
                            + "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                            + "(_thumb)?\\.(jpg|jpeg|png|gif|webp)");

    private final List<String> prefixes;

    @Autowired
    public ImageUrls(CoreProperties properties) {
        this(properties.image().publicBaseUrl(), properties.image().legacyBaseUrls());
    }

    private ImageUrls(String publicBaseUrl, List<String> legacyBaseUrls) {
        List<String> all = new ArrayList<>();
        all.add(prefixOf(publicBaseUrl));
        if (legacyBaseUrls != null) {
            legacyBaseUrls.stream().map(ImageUrls::prefixOf).forEach(all::add);
        }
        this.prefixes = List.copyOf(all);
    }

    /** 설정 없이 쓰는 생성 (단위 테스트·도구용). */
    public static ImageUrls of(String publicBaseUrl, List<String> legacyBaseUrls) {
        return new ImageUrls(publicBaseUrl, legacyBaseUrls);
    }

    /** 주소 → 저장 키. 우리 사진 주소가 아니면 빈 값. */
    public Optional<String> keyOf(String url) {
        return keyOfPrefixes(url, prefixes);
    }

    /** 지금 주소 또는 옛 주소로 쓴 우리 사진 주소인가. */
    public boolean isOurs(String url) {
        return keyOf(url).isPresent();
    }

    /** 주소 → 저장 키 (순수 함수). {@code baseUrls}는 공개 주소 목록(지금 + 옛). 002 테스트 스텁도 같은 규칙을 쓴다. */
    public static Optional<String> parseKey(String url, List<String> baseUrls) {
        return keyOfPrefixes(url, baseUrls.stream().map(ImageUrls::prefixOf).toList());
    }

    /** 저장 키 모양인가 (원본·썸네일). */
    public static boolean isStorageKey(String key) {
        return key != null && STORAGE_KEY.matcher(key).matches();
    }

    /** 썸네일 키 모양인가. */
    public static boolean isThumbnailKey(String key) {
        return isStorageKey(key) && key.contains("_thumb.");
    }

    private static Optional<String> keyOfPrefixes(String url, List<String> prefixes) {
        if (url == null || url.isEmpty()) {
            return Optional.empty();
        }
        for (String prefix : prefixes) {
            if (url.startsWith(prefix)) {
                String rest = url.substring(prefix.length());
                if (STORAGE_KEY.matcher(rest).matches()) {
                    return Optional.of(rest);
                }
            }
        }
        return Optional.empty();
    }

    private static String prefixOf(String base) {
        String b = base.strip();
        int end = b.length();
        while (end > 0 && b.charAt(end - 1) == '/') {
            end--;
        }
        return b.substring(0, end) + "/";
    }
}
