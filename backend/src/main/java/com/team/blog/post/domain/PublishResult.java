package com.team.blog.post.domain;

import java.time.Instant;

/**
 * 발행 결과 (contracts {@code PublishResponse} + 이벤트 판단 값).
 *
 * @param url 글 주소 {@code /@{handle}/posts/{id}} — 도메인은 모르므로 서비스가 {@link #withUrl}로 붙인다
 * @param publishedAt 최초 발행 시각
 * @param firstPublicAt 최초 공개 시각 (아직 전체 공개가 아니면 {@code null})
 * @param editedAt 다시 발행한 시각 (최초 발행이면 {@code null})
 * @param version 새 편집 버전
 * @param firstPublish 이번이 최초 발행인가 ({@code PostPublished})
 * @param wentPublic 이번에 처음 전체 공개가 되었나 ({@code PostWentPublic})
 */
public record PublishResult(
        String url,
        Instant publishedAt,
        Instant firstPublicAt,
        Instant editedAt,
        long version,
        boolean firstPublish,
        boolean wentPublic) {

    public PublishResult withUrl(String url) {
        return new PublishResult(
                url, publishedAt, firstPublicAt, editedAt, version, firstPublish, wentPublic);
    }
}
