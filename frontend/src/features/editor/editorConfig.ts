/**
 * 에디터 화면 수치 (002 T006, FR-007·FR-010·FR-047, 04 §2-1). 화면 쪽 설정값은 이 파일 한 곳에 둔다(constitution VII).
 *
 * - 로컬(IndexedDB) 저장: 입력이 1초 멈추면.
 * - 서버 자동 저장: 입력이 3초 멈추면, 계속 입력해도 30초마다 한 번은.
 * - 실패 재시도: 2s → 4s → 8s … 최대 60s, 여러 탭·기기가 한꺼번에 몰리지 않게 무작위 지연(jitter)을 더한다.
 * - 미리보기: 입력이 0.5초 멈추면 서버 렌더러 호출.
 * - [저장된 내용 불러오기] 백업: 7일 보관.
 * - 발행 409 `IN_PROGRESS`: 1초 뒤 같은 `Idempotency-Key`로 다시.
 */
export const EDITOR_CONFIG = Object.freeze({
  localSaveDebounceMs: 1000,
  serverSaveDebounceMs: 3000,
  serverSaveMaxWaitMs: 30000,
  retryBaseMs: 2000,
  retryMaxMs: 60000,
  /** 무작위 지연의 최대 비율 (기본 대기의 0~50%). */
  retryJitterRatio: 0.5,
  previewDebounceMs: 500,
  backupRetentionMs: 7 * 24 * 60 * 60 * 1000,
  inProgressRetryMs: 1000,
  /** 태그 최대 개수 (`blog.post.max-tags`와 같은 값, 008 화면이 교체). */
  maxTags: 10,
  /** 제목 최대 글자 수 (`blog.post.title-max`). */
  titleMax: 100,
});

/**
 * `attempt`번째(1부터) 재시도까지 기다릴 시간(ms). 기본 대기 = min(2s × 2^(attempt-1), 60s), 여기에 0 ~ 기본 대기 × 50%의
 * 무작위 지연을 더한다.
 *
 * @param random 0 이상 1 미만의 값을 주는 함수 (테스트에서 고정)
 */
export function retryDelayMs(attempt: number, random: () => number = Math.random): number {
  const n = Math.max(1, Math.floor(attempt));
  const { retryBaseMs, retryMaxMs, retryJitterRatio } = EDITOR_CONFIG;
  const base = Math.min(retryBaseMs * 2 ** Math.min(n - 1, 30), retryMaxMs);
  const jitter = Math.floor(base * retryJitterRatio * random());
  return base + jitter;
}
