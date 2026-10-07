import { describe, expect, it } from 'vitest';
import { EDITOR_CONFIG, retryDelayMs } from './editorConfig';

describe('editorConfig (002 T006, FR-007·010·047, 04 §2-1)', () => {
  it('화면 수치 기본값', () => {
    expect(EDITOR_CONFIG.localSaveDebounceMs).toBe(1000);
    expect(EDITOR_CONFIG.serverSaveDebounceMs).toBe(3000);
    expect(EDITOR_CONFIG.serverSaveMaxWaitMs).toBe(30000);
    expect(EDITOR_CONFIG.retryBaseMs).toBe(2000);
    expect(EDITOR_CONFIG.retryMaxMs).toBe(60000);
    expect(EDITOR_CONFIG.previewDebounceMs).toBe(500);
    expect(EDITOR_CONFIG.backupRetentionMs).toBe(7 * 24 * 60 * 60 * 1000);
    expect(EDITOR_CONFIG.inProgressRetryMs).toBe(1000);
  });

  it('바꿀 수 없다', () => {
    expect(Object.isFrozen(EDITOR_CONFIG)).toBe(true);
  });

  it('재시도 대기는 2s→4s→8s… 로 늘고 60s에서 멈춘다 (무작위 지연 0)', () => {
    const noJitter = () => 0;
    expect([1, 2, 3, 4, 5, 6, 7, 10].map((n) => retryDelayMs(n, noJitter))).toEqual([
      2000, 4000, 8000, 16000, 32000, 60000, 60000, 60000,
    ]);
  });

  it('무작위 지연은 기본 대기에 더해지고 기본 대기의 절반을 넘지 않는다', () => {
    expect(retryDelayMs(1, () => 0.999999)).toBeLessThanOrEqual(3000);
    expect(retryDelayMs(1, () => 0.5)).toBe(2500);
    expect(retryDelayMs(6, () => 0.5)).toBe(75000);
  });

  it('0 이하 시도 번호는 첫 시도로 본다', () => {
    expect(retryDelayMs(0, () => 0)).toBe(2000);
    expect(retryDelayMs(-3, () => 0)).toBe(2000);
  });
});
