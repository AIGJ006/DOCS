package com.team.blog.discovery.application;

import java.time.Instant;

/**
 * 글 카드 (contracts {@code PostCard}, data-model §5, 10 §2·§4-2). 본문 필드는 없다. 홈·블로그 목록이 같은 형식을 쓰고, 블로그
 * 화면은 작성자 영역만 그리지 않는다.
 *
 * @param id 글 번호
 * @param url 글 주소 {@code /@{handle}/posts/{id}}
 * @param title 제목 (글자 그대로 — 화면이 텍스트로 출력)
 * @param excerpt 미리보기 요약 (없으면 {@code null})
 * @param thumbnailUrl 640px 썸네일 주소 (없으면 {@code null})
 * @param firstPublicAt 최초 공개 일자 (UTC, 마이크로초) — 정렬·카드 날짜 기준
 * @param commentCount 댓글 수 (0도 표시)
 * @param likeCount 좋아요 수 (0도 표시)
 * @param author 작성자
 */
public record PostCardView(
        long id,
        String url,
        String title,
        String excerpt,
        String thumbnailUrl,
        Instant firstPublicAt,
        int commentCount,
        int likeCount,
        Author author) {

    /**
     * @param handle 블로그 주소
     * @param nickname 닉네임
     * @param profileImageUrl 작은 프로필 사진 주소 (썸네일, 없으면 원본; 사진이 없으면 {@code null} → 화면 기본 아이콘)
     */
    public record Author(String handle, String nickname, String profileImageUrl) {}
}
