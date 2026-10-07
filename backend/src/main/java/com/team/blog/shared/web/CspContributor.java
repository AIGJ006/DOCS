package com.team.blog.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * 화면별 CSP 확장 지점 (H3, R-21). Bean으로 등록하면 {@link #appliesTo}가 참인 응답에만 {@code img-src} 출처를 더한다. 예: 소셜
 * 가입 마무리 화면({@code /signup/social})에만 Google·GitHub 사진 호스트(001 T082).
 */
public interface CspContributor {

    boolean appliesTo(HttpServletRequest request);

    /** 더할 {@code img-src} 출처 (예: {@code https://lh3.googleusercontent.com}). */
    List<String> extraImgSrc();
}
