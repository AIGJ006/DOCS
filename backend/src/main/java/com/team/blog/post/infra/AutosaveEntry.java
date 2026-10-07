package com.team.blog.post.infra;

import java.time.Instant;

/**
 * Redis 자동 저장 보관분 {@code autosave:post:{postId}} Hash 한 건 (data-model §4).
 *
 * @param memberId 저장한 회원 (작성자)
 * @param title 제목 (입력 그대로)
 * @param contentMd 본문
 * @param version 이 내용의 편집 버전
 * @param savedAt 받아들인 시각
 */
public record AutosaveEntry(
        long memberId, String title, String contentMd, long version, Instant savedAt) {}
