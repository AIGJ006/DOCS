package com.team.blog.post.infra;

import com.team.blog.post.domain.PostStatus;
import com.team.blog.post.domain.Visibility;
import java.time.Instant;

/**
 * 읽기 판정용 투영 (004 data-model §2). {@code post p JOIN member m} 한 번으로 읽는다({@link
 * PostQueryRepository#findPostView(long)}). 휴지통 행도 담아 {@code PostAccessPolicy}가 먼저 거르게 한다.
 *
 * @param id 글 번호
 * @param authorId 작성자 회원 번호
 * @param status 글 상태
 * @param visibility 공개 범위
 * @param deletedAt 휴지통으로 옮긴 시각 (없으면 {@code null})
 * @param hiddenAt 관리자 숨김 시각 (없으면 {@code null})
 * @param authorWithdrawnAt 작성자 탈퇴 신청 시각 ({@code member.withdrawn_at}, 없으면 {@code null})
 */
public record PostView(
        long id,
        long authorId,
        PostStatus status,
        Visibility visibility,
        Instant deletedAt,
        Instant hiddenAt,
        Instant authorWithdrawnAt) {

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isHidden() {
        return hiddenAt != null;
    }

    public boolean isAuthorWithdrawn() {
        return authorWithdrawnAt != null;
    }
}
