/**
 * 좋아요 수·조회수 형식 (009 T011, FR-016, 31 §7). 0~9,999는 천 단위 쉼표(`1,234`), 1만 이상은 소수 첫째 자리까지 내림한
 * "만" 단위(`1.2만`, 12,999 → `1.2만`). 005 `ReactionBar`의 조회수 식을 꺼내 좋아요 수와 함께 쓴다.
 */
export function formatCount(value: number): string {
  if (value < 10_000) {
    return value.toLocaleString('ko-KR');
  }
  return `${Math.floor(value / 1_000) / 10}만`;
}
