package com.team.blog.media.application;

import com.team.blog.shared.config.CoreProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 저장소 키 → 공개 버킷 직접 주소 = {@code blog.image.public-base-url} + {@code /} + key (중복 {@code /} 정리).
 *
 * <p>임시 구현이 아니라 최종 규칙이며 003이 그대로 소유한다. CSP {@code img-src}도 같은 설정값의 출처를 쓴다(T029).
 */
@Component
public class ImageUrlResolver {

    private final String base;

    @Autowired
    public ImageUrlResolver(CoreProperties properties) {
        this(properties.image().publicBaseUrl());
    }

    private ImageUrlResolver(String publicBaseUrl) {
        String b = publicBaseUrl.strip();
        int end = b.length();
        while (end > 0 && b.charAt(end - 1) == '/') {
            end--;
        }
        this.base = b.substring(0, end);
    }

    /** 설정 없이 쓰는 생성(단위 테스트·도구용). */
    public static ImageUrlResolver of(String publicBaseUrl) {
        return new ImageUrlResolver(publicBaseUrl);
    }

    /**
     * @param storageKey {@code image.storage_key} 또는 {@code thumb_storage_key}; {@code null}이면
     *     {@code null}
     * @throws IllegalArgumentException 빈 키
     */
    public String publicUrl(String storageKey) {
        if (storageKey == null) {
            return null;
        }
        String key = storageKey.strip();
        int start = 0;
        while (start < key.length() && key.charAt(start) == '/') {
            start++;
        }
        if (start == key.length()) {
            throw new IllegalArgumentException("저장소 키가 비어 있습니다");
        }
        return base + "/" + key.substring(start);
    }
}
