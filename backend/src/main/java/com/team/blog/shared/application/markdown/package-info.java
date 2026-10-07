/**
 * 본문 렌더러 API (002 소유, 12 §2 "한곳에 모음"): {@code ContentRenderer} 하나가 Markdown → 정화 HTML·요약·사진 키·썸네일을
 * 만든다. 발행·미리보기·다시 렌더링이 모두 이것을 쓴다. 구현은 {@code shared.infra.markdown}, 사진 판별은 포트 {@code
 * ImageReferenceResolver}(구현은 media 모듈)로 받아 shared가 media에 의존하지 않는다.
 */
package com.team.blog.shared.application.markdown;
