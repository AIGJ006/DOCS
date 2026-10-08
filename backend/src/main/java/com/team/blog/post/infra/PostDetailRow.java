package com.team.blog.post.infra;

import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.domain.Visibility;
import java.time.Instant;

/**
 * 글 상세 한 행 (005 T034, data-model §1, 40 §6). {@code post p JOIN member m LEFT JOIN image pi}를 한 번
 * 읽은 결과이고 {@code content_md}는 담지 않는다. 휴지통 행도 담아 {@code PostAccessPolicy}가 먼저 거르게 한다.
 *
 * @param id 글 번호
 * @param authorId 작성자 회원 번호
 * @param title 제목
 * @param contentHtml 발행 때 정화된 본문 (마지막 발행본)
 * @param status 글 상태
 * @param visibility 공개 범위
 * @param viewCount 조회 수 (저장값 그대로)
 * @param likeCount 좋아요 수
 * @param commentCount 댓글 수
 * @param publishedAt 최초 발행 일자
 * @param firstPublicAt 최초 공개 일자 (비공개로만 발행했으면 {@code null})
 * @param editedAt 재발행 일자 (없으면 {@code null})
 * @param deletedAt 휴지통 (없으면 {@code null})
 * @param hiddenAt 관리자 숨김 (없으면 {@code null})
 * @param hiddenReason 숨김 사유 (표시 규칙은 014)
 * @param thumbnailUrl 대표 사진 썸네일 주소 (og:image 원본 찾기에 쓴다 — US5)
 * @param excerpt 요약 (미리보기 설명에 쓴다 — US5)
 * @param handle 작성자 블로그 주소
 * @param nickname 작성자 닉네임
 * @param bio 작성자 소개
 * @param authorWithdrawnAt 작성자 탈퇴 신청 시각
 * @param profileKey 작은 프로필 사진 키 {@code COALESCE(thumb_storage_key, storage_key)}
 */
public record PostDetailRow(
        long id,
        long authorId,
        String title,
        String contentHtml,
        PostStatus status,
        Visibility visibility,
        long viewCount,
        int likeCount,
        int commentCount,
        Instant publishedAt,
        Instant firstPublicAt,
        Instant editedAt,
        Instant deletedAt,
        Instant hiddenAt,
        String hiddenReason,
        String thumbnailUrl,
        String excerpt,
        String handle,
        String nickname,
        String bio,
        Instant authorWithdrawnAt,
        String profileKey) {

    /** 004 읽기 판정({@code PostAccessPolicy.canRead})에 넘길 투영. */
    public PostView toPostView() {
        return new PostView(
                id, authorId, status, visibility, deletedAt, hiddenAt, authorWithdrawnAt);
    }

    public boolean isHidden() {
        return hiddenAt != null;
    }
}
