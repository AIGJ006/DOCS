/**
 * 태그 주소 (008 contracts/normalization.md §3, research R10). 태그 링크는 이 함수로만 만든다 — `#`을 인코딩하지 않으면
 * 브라우저가 조각(fragment)으로 읽는다.
 *
 * - 경로: `encodeURIComponent`에서 `%2B`만 `+`로 되돌린다(서버 `UriUtils.encodePathSegment`와 같은 모양 — 경로에서 `+`는
 *   공백이 아니다). `c#` → `/tags/c%23`, `c++` → `/tags/c++`.
 * - 쿼리 값(`?tag=`): `encodeURIComponent` 그대로(쿼리에서는 `+`가 공백이라 `%2B`를 유지).
 */

/** 태그 페이지 경로. */
export function tagPath(name: string): string {
  return '/tags/' + tagPathSegment(name);
}

/** 경로 조각 하나 (API 경로에도 쓴다). */
export function tagPathSegment(name: string): string {
  return encodeURIComponent(name).replace(/%2B/g, '+');
}

/** 블로그 필터 `?tag=` 값. */
export function tagQueryValue(name: string): string {
  return encodeURIComponent(name);
}
