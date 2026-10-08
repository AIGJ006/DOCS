package com.team.blog.account.application;

import java.time.Instant;

/**
 * 탈퇴 안내 숫자 ({@code GET /api/me/withdrawal}, 015 data-model §3-1, FR-003). 화면을 여는 순간의 값이다.
 *
 * @param handle 블로그 주소 (다시 쓸 수 없음)
 * @param postCount 내 글 전부 (임시·발행·휴지통·숨김)
 * @param commentCount 남의 글에 쓴 지워지지 않은 댓글
 * @param receivedLikeCount 내 글이 받은 좋아요 합
 * @param restoreDeadline 지금 신청하면의 복구 기한
 * @param verification 본인 확인 방법
 */
public record WithdrawalPreview(
        String handle,
        long postCount,
        long commentCount,
        long receivedLikeCount,
        Instant restoreDeadline,
        Verification verification) {

    /** 본인 확인 방법: 이메일 가입 = 현재 비밀번호, 소셜 가입 = 확인 문구 입력. */
    public enum Verification {
        PASSWORD,
        CONFIRM_TEXT
    }
}
