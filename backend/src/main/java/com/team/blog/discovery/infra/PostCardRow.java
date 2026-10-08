package com.team.blog.discovery.infra;

import java.time.OffsetDateTime;

/**
 * 카드 조회 한 행 ({@link PostCardQueryRepository}). 본문 컬럼은 없다.
 *
 * @param id 글 번호
 * @param title 제목
 * @param excerpt 요약 (없으면 {@code null})
 * @param thumbnailUrl 썸네일 주소 (없으면 {@code null})
 * @param firstPublicAt 최초 공개 일자 (UTC, 마이크로초 그대로)
 * @param commentCount 댓글 수
 * @param likeCount 좋아요 수
 * @param handle 작성자 블로그 주소
 * @param nickname 작성자 닉네임
 * @param profileKey 작은 프로필 사진 저장소 키 = {@code COALESCE(thumb_storage_key, storage_key)} (없으면 {@code
 *     null})
 */
public record PostCardRow(
        long id,
        String title,
        String excerpt,
        String thumbnailUrl,
        OffsetDateTime firstPublicAt,
        int commentCount,
        int likeCount,
        String handle,
        String nickname,
        String profileKey) {}
