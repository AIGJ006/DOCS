package com.team.blog.media.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * 저장 키 만들기 (research R7, 04 §4-2, 23 §2-4): {@code images/{yyyy}/{MM}/{uuid}.{ext}}, 썸네일 {@code
 * images/{yyyy}/{MM}/{uuid}_thumb.{ext}}. 날짜는 presign 시각의 서비스 시간대({@code blog.time-zone}) 기준, uuid는
 * 소문자 무작위이며 원본과 썸네일이 같다. 원래 파일 이름은 받지 않는다(FR-009).
 */
@Component
public class StorageKeys {

    /**
     * @param original 원본 키
     * @param thumb 썸네일 키 (썸네일이 없으면 {@code null})
     */
    public record Pair(String original, String thumb) {}

    private final Clock clock;
    private final ZoneId zone;

    public StorageKeys(Clock clock, ZoneId serviceZoneId) {
        this.clock = clock;
        this.zone = serviceZoneId;
    }

    /**
     * @param format 원본 형식
     * @param thumbFormat 썸네일 형식 ({@code null}이면 썸네일 키 없음 — 프로필 사진)
     */
    public Pair newPair(ImageFormat format, ImageFormat thumbFormat) {
        LocalDate today = LocalDate.now(clock.withZone(zone));
        String prefix =
                "images/%04d/%02d/%s"
                        .formatted(today.getYear(), today.getMonthValue(), UUID.randomUUID());
        return new Pair(
                prefix + "." + format.extension(),
                thumbFormat == null ? null : prefix + "_thumb." + thumbFormat.extension());
    }
}
