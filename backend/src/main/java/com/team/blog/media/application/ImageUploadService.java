package com.team.blog.media.application;

import com.team.blog.media.domain.ImageFormat;
import com.team.blog.media.domain.ImageHeaderReader;
import com.team.blog.media.domain.ImageInspection;
import com.team.blog.media.domain.ImagePurpose;
import com.team.blog.media.domain.ImageRejectReason;
import com.team.blog.media.domain.MediaReasonCode;
import com.team.blog.media.domain.StorageKeys;
import com.team.blog.media.infra.ImageRepository;
import com.team.blog.media.infra.ImageRepository.ImageRow;
import com.team.blog.media.infra.storage.ImageStorage;
import com.team.blog.media.infra.storage.ImageStorage.StoredObject;
import com.team.blog.media.infra.storage.ImageStorage.UploadTarget;
import com.team.blog.shared.error.BusinessRuleException;
import com.team.blog.shared.error.FieldError;
import com.team.blog.shared.error.NotFoundException;
import com.team.blog.shared.error.TooManyRequestsException;
import com.team.blog.shared.error.ValidationException;
import com.team.blog.shared.infra.ratelimit.RateLimitResult;
import com.team.blog.shared.infra.ratelimit.RateLimiter;
import com.team.blog.shared.security.AccountStatusGuard;
import com.team.blog.shared.security.ActionKind;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 사진 업로드 준비·완료 확인 (003 T028·T029·T059·T076, research R5·R8·R24, contracts/openapi.yaml).
 *
 * <p>presign 판정 순서(R8): 401(보안 필터) → 403 계정 상태 → 400 칸 → 409 용량(트랜잭션 A, 회원 행 잠금) → 커밋 → 429 하루
 * 장수(Redis) → 429 1분 장수({@link RateLimiter}) → 거부면 보상 삭제(트랜잭션 B) → 주소 서명. Redis 장애면 두 장수 제한을
 * 건너뛴다(02 §2-1).
 *
 * <p>complete: 업로더 확인(아니면 404) → 이미 완료면 저장된 값(멱등) → 트랜잭션 밖에서 저장소 확인(존재·크기·머리말·GIF 프레임) → 실패면 두 파일과
 * 행을 지우고 400 → 통과면 짧은 트랜잭션에서 다시 잠그고 기록. 저장소에 닿지 못하면 503이고 행은 남는다(정리 작업이 24시간 뒤 지운다).
 *
 * <p>로그에는 사진 번호·키·크기·회원 번호만 남긴다. 업로드 주소(서명 쿼리)는 남기지 않는다.
 */
@Service
public class ImageUploadService {

    private static final Logger log = LoggerFactory.getLogger(ImageUploadService.class);

    /** 칸 오류: 값 없음. */
    static final String REQUIRED = "REQUIRED";

    /** 칸 오류: 모르는 칸 (파일 이름 등). */
    static final String UNKNOWN_FIELD = "UNKNOWN_FIELD";

    /** 칸 오류: 값이 범위 밖 (0 이하 크기·모르는 용도). */
    static final String INVALID_VALUE = "INVALID_VALUE";

    private static final Set<ImageFormat> THUMB_FORMATS =
            Set.of(ImageFormat.WEBP, ImageFormat.JPEG);

    /** 프로필 사진 형식 (R14 WebP + 사파리 대체 JPEG — 구현 메모 T028). */
    private static final Set<ImageFormat> PROFILE_FORMATS =
            Set.of(ImageFormat.WEBP, ImageFormat.JPEG);

    private final AccountStatusGuard statusGuard;
    private final ImageRepository images;
    private final ImageStorage storage;
    private final StorageKeys keys;
    private final RateLimiter rateLimiter;
    private final StorageQuotaService limits;
    private final ImageUrlResolver urls;
    private final ImageProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ImageUploadService(
            AccountStatusGuard statusGuard,
            ImageRepository images,
            ImageStorage storage,
            StorageKeys keys,
            RateLimiter rateLimiter,
            StorageQuotaService limits,
            ImageUrlResolver urls,
            ImageProperties properties,
            TransactionTemplate tx,
            Clock clock) {
        this.statusGuard = statusGuard;
        this.images = images;
        this.storage = storage;
        this.keys = keys;
        this.rateLimiter = rateLimiter;
        this.limits = limits;
        this.urls = urls;
        this.properties = properties;
        this.tx = tx;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ presign

    /** 업로드 준비: 행(TEMP·완료 전)을 만들고 업로드 주소를 준다. */
    public ImageUploadTicket presign(long memberId, PresignCommand command) {
        statusGuard.requireActive(memberId, ActionKind.CONTENT_WRITE);
        Validated request = validate(command);
        Instant now = clock.instant();
        StorageKeys.Pair pair = keys.newPair(request.format(), request.thumbFormat());

        long imageId =
                tx.execute(
                        status -> {
                            limits.checkQuota(memberId, request.totalBytes());
                            return images.insertTemp(
                                    memberId,
                                    request.purpose(),
                                    pair.original(),
                                    pair.thumb(),
                                    request.format().mimeType(),
                                    request.size(),
                                    request.thumbSize(),
                                    now);
                        });

        try {
            limits.reserveDaily(memberId, now);
            RateLimitResult minute =
                    rateLimiter.tryAcquire(
                            "ratelimit:image:" + memberId,
                            properties.perMinuteLimit(),
                            Duration.ofMinutes(1));
            if (minute instanceof RateLimitResult.Denied denied) {
                limits.releaseDaily(memberId, now);
                throw new TooManyRequestsException(denied.retryAfterSeconds());
            }
        } catch (RuntimeException denied) {
            tx.executeWithoutResult(status -> images.deleteById(imageId));
            throw denied;
        }

        Duration ttl = properties.presignTtl();
        UploadTarget upload =
                storage.prepareUpload(
                        pair.original(), request.format().mimeType(), request.size(), ttl);
        UploadTarget thumbUpload =
                pair.thumb() == null
                        ? null
                        : storage.prepareUpload(
                                pair.thumb(),
                                request.thumbFormat().mimeType(),
                                request.thumbSize(),
                                ttl);
        log.info(
                "사진 업로드 준비: imageId={}, member={}, key={}, size={}, thumbSize={}",
                imageId,
                memberId,
                pair.original(),
                request.size(),
                request.thumbSize());
        return new ImageUploadTicket(imageId, upload, thumbUpload, upload.expiresAt());
    }

    /** 검사한 요청. */
    private record Validated(
            ImagePurpose purpose,
            ImageFormat format,
            int size,
            ImageFormat thumbFormat,
            Integer thumbSize) {

        long totalBytes() {
            return (long) size + (thumbSize == null ? 0 : thumbSize);
        }
    }

    private Validated validate(PresignCommand c) {
        List<FieldError> errors = new ArrayList<>();
        for (String field : c.unknownFields()) {
            errors.add(new FieldError(field, UNKNOWN_FIELD, "받지 않는 칸이에요"));
        }

        ImagePurpose purpose = null;
        if (c.purpose() == null || c.purpose().isBlank()) {
            errors.add(new FieldError("purpose", REQUIRED, "필수 값이에요"));
        } else {
            try {
                purpose = ImagePurpose.valueOf(c.purpose());
            } catch (IllegalArgumentException e) {
                errors.add(new FieldError("purpose", INVALID_VALUE, "입력한 내용을 확인해 주세요"));
            }
        }
        boolean profile = purpose == ImagePurpose.PROFILE;

        ImageFormat format = null;
        if (c.contentType() == null || c.contentType().isBlank()) {
            errors.add(new FieldError("contentType", REQUIRED, "필수 값이에요"));
        } else {
            format = ImageFormat.ofMimeType(c.contentType()).orElse(null);
            if (format == null || (profile && !PROFILE_FORMATS.contains(format))) {
                errors.add(MediaReasonCode.UNSUPPORTED_IMAGE_TYPE.fieldError("contentType"));
                format = null;
            }
        }

        long maxSize = profile ? properties.profileMaxBytes() : properties.maxUploadBytes();
        if (c.size() == null) {
            errors.add(new FieldError("size", REQUIRED, "필수 값이에요"));
        } else if (c.size() < 1) {
            errors.add(new FieldError("size", INVALID_VALUE, "입력한 내용을 확인해 주세요"));
        } else if (c.size() > maxSize) {
            errors.add(MediaReasonCode.IMAGE_TOO_LARGE.fieldError("size"));
        }

        ImageFormat thumbFormat = null;
        boolean hasThumbField = c.thumbContentType() != null || c.thumbSize() != null;
        if (profile) {
            if (hasThumbField) {
                errors.add(
                        MediaReasonCode.THUMBNAIL_NOT_ALLOWED.fieldError(
                                c.thumbContentType() != null ? "thumbContentType" : "thumbSize"));
            }
        } else if (purpose == ImagePurpose.POST) {
            if (c.thumbContentType() == null) {
                errors.add(MediaReasonCode.THUMBNAIL_REQUIRED.fieldError("thumbContentType"));
            } else {
                thumbFormat = ImageFormat.ofMimeType(c.thumbContentType()).orElse(null);
                if (thumbFormat == null || !THUMB_FORMATS.contains(thumbFormat)) {
                    errors.add(
                            MediaReasonCode.UNSUPPORTED_IMAGE_TYPE.fieldError("thumbContentType"));
                    thumbFormat = null;
                }
            }
            if (c.thumbSize() == null) {
                errors.add(MediaReasonCode.THUMBNAIL_REQUIRED.fieldError("thumbSize"));
            } else if (c.thumbSize() < 1) {
                errors.add(new FieldError("thumbSize", INVALID_VALUE, "입력한 내용을 확인해 주세요"));
            } else if (c.thumbSize() > properties.maxThumbBytes()) {
                errors.add(MediaReasonCode.THUMBNAIL_TOO_LARGE.fieldError("thumbSize"));
            }
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new Validated(
                purpose,
                format,
                c.size().intValue(),
                thumbFormat,
                profile ? null : c.thumbSize().intValue());
    }

    // ------------------------------------------------------------------ complete

    /** 업로드 완료 확인. 남의 사진·없는 사진은 404. */
    public UploadedImage complete(long memberId, long imageId) {
        statusGuard.requireActive(memberId, ActionKind.CONTENT_WRITE);
        ImageRow row = images.findOwned(imageId, memberId).orElseThrow(NotFoundException::new);
        if (row.completed()) {
            return view(row);
        }

        Optional<StoredObject> original = storage.head(row.storageKey());
        Optional<StoredObject> thumb =
                row.thumbStorageKey() == null
                        ? Optional.empty()
                        : storage.head(row.thumbStorageKey());
        if (original.isEmpty() || (row.thumbStorageKey() != null && thumb.isEmpty())) {
            discard(row);
            log.info("사진 업로드 완료 확인 실패: imageId={}, member={}, 파일 없음", imageId, memberId);
            throw new BusinessRuleException(MediaReasonCode.IMAGE_NOT_UPLOADED);
        }

        Inspected inspected = inspect(row, original.get(), thumb.orElse(null));
        if (inspected.reason() != null) {
            discard(row);
            log.info(
                    "사진 업로드 거부: imageId={}, member={}, key={}, reason={}",
                    imageId,
                    memberId,
                    row.storageKey(),
                    inspected.reason());
            throw new BusinessRuleException(
                    MediaReasonCode.IMAGE_REJECTED, Map.of("reason", inspected.reason().name()));
        }

        ImageRow done =
                tx.execute(
                        status -> {
                            ImageRow locked =
                                    images.lockOwned(imageId, memberId)
                                            .orElseThrow(NotFoundException::new);
                            if (locked.completed()) {
                                return locked;
                            }
                            images.markCompleted(
                                    imageId,
                                    (int) original.get().size(),
                                    thumb.map(t -> (int) t.size()).orElse(null),
                                    inspected.width(),
                                    inspected.height());
                            return images.findOwned(imageId, memberId).orElseThrow();
                        });
        log.info(
                "사진 업로드 완료: imageId={}, member={}, key={}, size={}",
                imageId,
                memberId,
                done.storageKey(),
                done.sizeBytes());
        return view(done);
    }

    private UploadedImage view(ImageRow row) {
        return new UploadedImage(
                row.id(),
                urls.publicUrl(row.storageKey()),
                row.thumbStorageKey() == null ? null : urls.publicUrl(row.thumbStorageKey()),
                row.contentType(),
                row.width(),
                row.height(),
                row.sizeBytes());
    }

    /** 검사 결과: 통과면 가로·세로, 실패면 사유. */
    private record Inspected(ImageRejectReason reason, int width, int height) {
        static Inspected reject(ImageRejectReason reason) {
            return new Inspected(reason, 0, 0);
        }
    }

    private Inspected inspect(ImageRow row, StoredObject original, StoredObject thumb) {
        if (original.size() > row.sizeBytes()
                || (thumb != null
                        && row.thumbSizeBytes() != null
                        && thumb.size() > row.thumbSizeBytes())) {
            return Inspected.reject(ImageRejectReason.SIZE_MISMATCH);
        }
        ImageFormat declared = ImageFormat.ofMimeType(row.contentType()).orElse(null);
        ImageInspection head =
                ImageHeaderReader.inspect(
                        storage.readHead(row.storageKey(), properties.inspectHeadBytes()));
        if (head.format() == null || head.format() != declared) {
            return Inspected.reject(ImageRejectReason.TYPE_MISMATCH);
        }
        if (!head.ok()) {
            return Inspected.reject(ImageRejectReason.CORRUPT);
        }
        int longSide = Math.max(head.width(), head.height());
        if (head.format() == ImageFormat.GIF) {
            if (longSide > properties.gifMaxSide()) {
                return Inspected.reject(ImageRejectReason.GIF_TOO_LARGE);
            }
            int frames = countGifFrames(row.storageKey());
            if (frames < 0) {
                return Inspected.reject(ImageRejectReason.CORRUPT);
            }
            if (frames > properties.gifMaxFrames()) {
                return Inspected.reject(ImageRejectReason.GIF_TOO_MANY_FRAMES);
            }
        } else if (longSide > properties.maxSide() || head.pixels() > properties.maxPixels()) {
            return Inspected.reject(ImageRejectReason.DIMENSION_EXCEEDED);
        }
        if (row.purpose() == ImagePurpose.PROFILE
                && (head.width() != properties.profileSide()
                        || head.height() != properties.profileSide())) {
            return Inspected.reject(ImageRejectReason.PROFILE_SIZE_INVALID);
        }
        if (thumb != null && !thumbOk(row.thumbStorageKey())) {
            return Inspected.reject(ImageRejectReason.THUMBNAIL_INVALID);
        }
        return new Inspected(null, head.width(), head.height());
    }

    private boolean thumbOk(String thumbKey) {
        String ext = thumbKey.substring(thumbKey.lastIndexOf('.') + 1);
        ImageFormat expected = ext.equals("jpg") ? ImageFormat.JPEG : ImageFormat.WEBP;
        ImageInspection head =
                ImageHeaderReader.inspect(
                        storage.readHead(thumbKey, properties.inspectHeadBytes()));
        return head.ok()
                && head.format() == expected
                && head.width() <= properties.thumbMaxWidth()
                && head.height() <= properties.thumbMaxHeight();
    }

    private int countGifFrames(String key) {
        try (InputStream in = storage.openStream(key)) {
            return ImageHeaderReader.countGifFrames(in, properties.gifMaxFrames());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 검사에 떨어진 사진: 두 파일을 지우고, 모두 지워졌으면 행도 지운다(남으면 정리 작업이 24시간 뒤 지운다). */
    private void discard(ImageRow row) {
        List<String> targets = new ArrayList<>();
        targets.add(row.storageKey());
        if (row.thumbStorageKey() != null) {
            targets.add(row.thumbStorageKey());
        }
        Set<String> failed = storage.deleteAll(targets);
        if (failed.isEmpty()) {
            tx.executeWithoutResult(status -> images.deleteIncomplete(row.id()));
        } else {
            log.warn("거부된 사진 파일을 지우지 못함: imageId={}, 남은 키 {}개", row.id(), failed.size());
        }
    }
}
