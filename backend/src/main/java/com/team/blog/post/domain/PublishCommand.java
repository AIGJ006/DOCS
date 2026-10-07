package com.team.blog.post.domain;

import java.util.List;

/**
 * 발행 명령 (05 §7). 작성자는 세션에서만 꺼낸다.
 *
 * @param postId 글 번호
 * @param memberId 로그인한 회원 (세션)
 * @param title 정리한 제목 ({@link TitleNormalizer})
 * @param contentMd 본문 Markdown
 * @param rawTags 입력한 태그 (정규화는 {@code TagService})
 * @param visibility 공개 범위
 * @param baseVersion 이 내용이 출발한 서버 편집 버전
 * @param idempotencyKey 요청 키 (US6 중복 판정)
 */
public record PublishCommand(
        long postId,
        long memberId,
        String title,
        String contentMd,
        List<String> rawTags,
        Visibility visibility,
        long baseVersion,
        String idempotencyKey) {

    public PublishCommand {
        rawTags = rawTags == null ? List.of() : List.copyOf(rawTags);
    }
}
