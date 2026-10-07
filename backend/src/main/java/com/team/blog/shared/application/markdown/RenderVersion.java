package com.team.blog.shared.application.markdown;

/**
 * 렌더링 규칙 버전 (12 §7-7, research A-11). 렌더러·정화 규칙을 바꾸면 {@link #CURRENT}를 올린다 → 다시 렌더링 배치가 {@code
 * render_version < CURRENT}인 발행 글을 새 규칙으로 다시 만든다. DB 기본값(1)과 같은 값에서 시작한다.
 */
public final class RenderVersion {

    public static final int CURRENT = 1;

    private RenderVersion() {}
}
