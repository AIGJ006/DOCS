package com.team.blog.media.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 사진 업로드 설정값 ({@code blog.image.*}, data-model §5, research R19, 헌법 VII). 기본값은 {@code
 * application.yml}에 둔다. {@code public-base-url}·{@code legacy-base-urls}는 001 {@code
 * CoreProperties.Image}가 바인딩하므로 여기서 다시 받지 않는다(같은 접두어를 나눠 바인딩).
 *
 * <p>시작할 때 검증한다: {@code max-upload-bytes ≤ 10485760}·{@code max-thumb-bytes ≤ 1048576}(V1 {@code
 * ck_image_size}·{@code ck_image_thumb_size}보다 크면 INSERT가 실패하므로 기동 실패로 알림), 저장소 키가 비면 기동 실패. 비밀값은
 * 환경 변수({@code BLOG_IMAGE_STORAGE_ACCESS_KEY}·{@code BLOG_IMAGE_STORAGE_SECRET_KEY})로만 넣는다.
 *
 * @param uploadMode 업로드 방식 (research R2: 운영 점검 10번이 실패하면 {@code PROXY})
 * @param quotaBytes 1인 저장 공간 (FR-013, 1GB)
 * @param dailyLimit 하루 presign 장수 (FR-013, 한국 시간 0시 기준)
 * @param perMinuteLimit 1분 presign 장수 (FR-011)
 * @param presignTtl 업로드 주소 유효 시간 (FR-005)
 * @param maxUploadBytes 올라가는 원본 최대 크기 (FR-001, Q3)
 * @param maxThumbBytes 썸네일 최대 크기
 * @param maxSourceBytes 브라우저가 처리할 원래 파일 상한 (Q3, 화면만 사용 — {@code GET /api/me/storage}의 {@code
 *     limits})
 * @param longSide 브라우저 줄이기 긴 변 (FR-002)
 * @param thumbMaxWidth 썸네일 최대 가로 (FR-003)
 * @param maxSide 서버 검사: GIF 아닌 원본 가로·세로 상한 (research R6)
 * @param maxPixels 서버 검사: 원본 전체 픽셀 상한
 * @param thumbMaxHeight 서버 검사: 썸네일 세로 상한 (research R6, 기본 {@code max-side}와 같은 4096)
 * @param gifMaxSide GIF 가로·세로 상한 (FR-036)
 * @param gifMaxFrames GIF 프레임 상한 (FR-036)
 * @param profileSide 프로필 사진 정확한 가로·세로 (001)
 * @param profileMaxBytes 프로필 사진 최대 크기
 * @param inspectHeadBytes complete 앞부분 읽기 크기 (research R6)
 * @param cleanup 버려진 사진 정리 배치 (04 §4-4)
 * @param storage 사진 저장소 접속 (S3 API)
 */
@Validated
@ConfigurationProperties("blog.image")
public record ImageProperties(
        @NotNull @DefaultValue("DIRECT") UploadMode uploadMode,
        @Min(1) @DefaultValue("1073741824") long quotaBytes,
        @Min(1) @DefaultValue("200") int dailyLimit,
        @Min(1) @DefaultValue("20") int perMinuteLimit,
        @NotNull @DefaultValue("PT5M") Duration presignTtl,
        @Min(1) @Max(10_485_760) @DefaultValue("10485760") int maxUploadBytes,
        @Min(1) @Max(1_048_576) @DefaultValue("1048576") int maxThumbBytes,
        @Min(1) @DefaultValue("52428800") long maxSourceBytes,
        @Min(1) @DefaultValue("1920") int longSide,
        @Min(1) @DefaultValue("640") int thumbMaxWidth,
        @Min(1) @DefaultValue("4096") int maxSide,
        @Min(1) @DefaultValue("16777216") long maxPixels,
        @Min(1) @DefaultValue("4096") int thumbMaxHeight,
        @Min(1) @DefaultValue("1920") int gifMaxSide,
        @Min(1) @DefaultValue("300") int gifMaxFrames,
        @Min(1) @DefaultValue("256") int profileSide,
        @Min(1) @Max(1_048_576) @DefaultValue("1048576") int profileMaxBytes,
        @Min(1024) @DefaultValue("65536") int inspectHeadBytes,
        @Valid @NotNull @DefaultValue Cleanup cleanup,
        @Valid @NotNull Storage storage) {

    /** 업로드 방식 (research R2). */
    public enum UploadMode {
        /** 브라우저가 저장소 Presigned PUT 주소로 직접 올린다 (기본). */
        DIRECT,
        /** 서버 경유 ({@code PUT /api/images/{imageId}/content}) — 운영 저장소 CORS가 막힐 때. */
        PROXY
    }

    /**
     * @param cron 실행 시각 ({@code blog.time-zone} 기준, 기본 매일 03:30)
     * @param tempTtl 완료·미완료와 상관없이 연결되지 않은 TEMP 사진 보관 시간 (24시간)
     * @param detachedTtl 연결 해제 뒤 보관 기간 (7일)
     * @param batchSize 한 묶음 크기 (S3 {@code DeleteObjects} 한도 1,000키와 맞춤)
     * @param maxDuration 1회 최대 실행 시간 (ShedLock {@code lockAtMostFor}와 같음)
     */
    public record Cleanup(
            @NotBlank @DefaultValue("0 30 3 * * *") String cron,
            @NotNull @DefaultValue("PT24H") Duration tempTtl,
            @NotNull @DefaultValue("P7D") Duration detachedTtl,
            @Min(1) @Max(1000) @DefaultValue("1000") int batchSize,
            @NotNull @DefaultValue("PT30M") Duration maxDuration) {}

    /**
     * @param endpoint 서버 → 저장소 주소 (compose 안 {@code http://minio:9000})
     * @param presignEndpoint 브라우저 → 저장소 주소 (서명 주소의 호스트, research R20)
     * @param region 서명 리전
     * @param bucket 버킷 ({@code blog})
     * @param accessKey 앱 전용 키 (환경 변수로만)
     * @param secretKey 앱 전용 비밀 키 (환경 변수로만)
     */
    public record Storage(
            @NotBlank String endpoint,
            @NotBlank String presignEndpoint,
            @NotBlank @DefaultValue("us-east-1") String region,
            @NotBlank @DefaultValue("blog") String bucket,
            @NotBlank String accessKey,
            @NotBlank String secretKey) {

        @Override
        public String toString() {
            // 비밀값은 로그·오류 메시지에 나오지 않게 한다
            return "Storage[endpoint=%s, presignEndpoint=%s, region=%s, bucket=%s]"
                    .formatted(endpoint, presignEndpoint, region, bucket);
        }
    }
}
