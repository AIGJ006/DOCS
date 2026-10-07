package com.team.blog.shared.application.markdown;

/**
 * 사진 판별 기준 (FR-044, 12 §6). 이 회원이 올린 우리 저장소 사진만 {@code <img>}가 되고 나머지는 링크가 된다.
 *
 * @param ownerMemberId 발행·다시 렌더링은 글 작성자, 미리보기는 로그인한 본인
 */
public record ImageContext(long ownerMemberId) {}
