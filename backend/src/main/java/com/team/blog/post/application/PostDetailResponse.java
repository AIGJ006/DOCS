package com.team.blog.post.application;

/**
 * {@code GET /api/posts/{postId}} 응답 (005 contracts {@code PostDetail}). 보통은 {@link
 * PostDetailView}이고, 작성자 본인이 자기 임시글을 열면 본문 없이 {@code id}·{@code status}·{@code editorPath}만 담은
 * {@link PostDetailView.Draft}다(research R-22, FR-026 ⑤) — 화면이 그 주소로 이동한다.
 */
public sealed interface PostDetailResponse permits PostDetailView, PostDetailView.Draft {

    long id();

    String status();
}
