package com.team.blog.media.infra.storage;

import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 사진 저장소 추상화 (003 T015, contracts/storage.md §1). 모든 메서드는 외부 호출이므로 DB 트랜잭션 밖에서 부른다({@link
 * #prepareUpload}만 예외 — 서명은 로컬 계산). 저장소에 닿지 못하면 {@link StorageUnavailableException}(503).
 */
public interface ImageStorage {

    /** 모든 사진 객체의 {@code Cache-Control} (FR-029). */
    String CACHE_CONTROL = "public, max-age=31536000, immutable";

    /**
     * 브라우저가 올릴 주소.
     *
     * @param url Presigned PUT 주소 (서명 쿼리 포함 — 로그에 남기지 않는다)
     * @param method 항상 {@code PUT}
     * @param headers 브라우저가 그대로 붙일 헤더(서명에 포함): {@code Content-Type}·{@code Cache-Control}. {@code
     *     Content-Length}는 브라우저가 넣는다
     * @param expiresAt 만료 시각
     */
    record UploadTarget(
            String url, String method, Map<String, String> headers, Instant expiresAt) {}

    /**
     * 저장된 객체의 머리 정보.
     *
     * @param size 실제 바이트 수
     * @param contentType 저장된 {@code Content-Type}
     * @param cacheControl 저장된 {@code Cache-Control}
     */
    record StoredObject(long size, String contentType, String cacheControl) {}

    /**
     * Presigned PUT 주소를 만든다. {@code Content-Type}·{@code Content-Length}·{@code Cache-Control}을
     * 서명한다.
     */
    UploadTarget prepareUpload(String key, String contentType, long size, Duration ttl);

    /** 객체가 있으면 머리 정보, 없으면 빈 값 ({@code HeadObject}). */
    Optional<StoredObject> head(String key);

    /** 앞부분 {@code maxBytes}까지 읽는다 (범위 GET). 없으면 빈 배열. */
    byte[] readHead(String key, int maxBytes);

    /** 객체 전체 스트림 (GIF 프레임 세기). 호출자가 닫는다. 없으면 {@link java.util.NoSuchElementException}. */
    InputStream openStream(String key);

    /**
     * 여러 키를 지운다 ({@code DeleteObjects}, 1,000키씩). 없는 키도 성공으로 본다.
     *
     * @return 지우지 못한 키 (모두 성공이면 빈 집합)
     */
    Set<String> deleteAll(Collection<String> keys);

    /** 서버가 받은 바이트를 그대로 올린다 (PROXY 모드). */
    void put(String key, String contentType, long size, InputStream content);
}
