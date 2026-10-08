package com.team.blog.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 화면별 CSP 확장 지점 (H3, R-21). Bean으로 등록하면 {@link #appliesTo}가 참인 응답에만 {@code img-src}·{@code
 * connect-src} 출처를 더한다. 예: 소셜 가입 마무리 화면({@code /signup/social})에만 Google·GitHub 사진 호스트(001 T082),
 * 모든 화면에 사진 업로드 주소 출처(003 T019 {@code connect-src}).
 */
public interface CspContributor {

    boolean appliesTo(HttpServletRequest request);

    /** 더할 {@code img-src} 출처 (예: {@code https://lh3.googleusercontent.com}). */
    List<String> extraImgSrc();

    /** 더할 {@code connect-src} 출처 (003 T019 추가, 기본 없음). */
    default List<String> extraConnectSrc() {
        return List.of();
    }
}
