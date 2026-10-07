/**
 * 본문 렌더러 구현 (12 §2 ①~④): commonmark-java 파싱 → AST 변환 → HTML 렌더링 → OWASP 정화 → 요약·썸네일. 바깥에서는 {@code
 * shared.application.markdown.ContentRenderer}만 쓴다.
 */
package com.team.blog.shared.infra.markdown;
