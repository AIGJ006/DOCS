package com.team.blog.media.application;

import java.util.Optional;

/**
 * 프로필 사진 연결 포트 (001 T116, data-model §2-6, R-20). account의 프로필 저장 트랜잭션 안에서 부른다(회원 행 {@code FOR
 * UPDATE}로 같은 회원의 저장이 직렬화된다). 구현은 003 {@link DefaultProfileImageService}(001 T116 임시 구현을 대신함).
 *
 * <p>현재 사진 = {@code uq_image_profile_current} 조건({@code purpose = 'PROFILE' AND status = 'ATTACHED'
 * AND detached_at IS NULL})의 행 하나. 저장소 키 조회는 {@link ProfileImageQuery}가 맡는다.
 */
public interface ProfileImageService {

    /** 칸 오류 코드 (openapi {@code updateMyProfile} 400). */
    String INVALID_PROFILE_IMAGE = "INVALID_PROFILE_IMAGE";

    String INVALID_PROFILE_IMAGE_MESSAGE = "사용할 수 없는 사진이에요";

    /** 그 회원이 올린 {@code purpose = 'PROFILE'} 사진인가 (저장 전 검사 — 오류를 다른 칸과 함께 모으기 위해). */
    boolean isAttachable(long memberId, long imageId);

    /**
     * 이전 사진을 떼고({@code detached_at = now()}) 새 사진을 붙인다({@code status = 'ATTACHED', detached_at =
     * NULL}).
     *
     * @throws com.team.blog.shared.error.ValidationException 400 {@code INVALID_PROFILE_IMAGE} — 남의
     *     사진·글용 사진·없는 사진
     */
    void attach(long memberId, long imageId);

    /** 현재 사진을 뗀다(기본 이미지로). 없으면 아무것도 하지 않는다. */
    void detach(long memberId);

    /** 현재 사진 ID. */
    Optional<Long> currentImageId(long memberId);
}
