package com.team.blog.post.domain;

import java.time.Instant;

/**
 * 서버가 가진 가장 최근 내용 (data-model §6). 409 {@code VERSION_CONFLICT}의 {@code details.server}와 에디터 열기 응답에
 * 쓴다. 저장하지 않는 값 객체다.
 *
 * @param title 제목 (입력 그대로)
 * @param contentMd 본문 Markdown
 * @param version 현재 편집 버전 = max(Redis {@code version}, {@code post_draft.edit_version}, {@code
 *     post.edit_version})
 * @param savedAt 그 내용을 저장한 시각
 */
public record ServerCopy(String title, String contentMd, long version, Instant savedAt) {}
