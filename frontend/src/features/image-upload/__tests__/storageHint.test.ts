import { describe, expect, it } from 'vitest';
import type { StorageUsage } from '../../../api/types/images';
import { formatBytes, storageHint } from '../storageHint';
import { UPLOAD_MESSAGES } from '../uploadMessages';

const GB = 1_073_741_824;
const MB = 1_048_576;

function usage(usedBytes: number): StorageUsage {
  return {
    usedBytes,
    quotaBytes: GB,
    todayCount: 3,
    dailyLimit: 200,
    limits: {
      maxUploadBytes: 10_485_760,
      maxThumbBytes: 1_048_576,
      maxSourceBytes: 52_428_800,
      longSide: 1920,
      thumbMaxWidth: 640,
      gifMaxSide: 1920,
      gifMaxFrames: 300,
    },
  };
}

describe('storageHint', () => {
  it('사용량이 90%를 넘으면 남은 공간을 알려 준다', () => {
    expect(storageHint(usage(GB - 100 * MB))).toBe('남은 공간 약 100MB');
  });

  it('90% 이하면 안내 없음', () => {
    expect(storageHint(usage(GB * 0.9))).toBeNull();
    expect(storageHint(usage(312 * MB))).toBeNull();
    expect(storageHint(null)).toBeNull();
  });

  it('409·하루 한도 문구는 data-model §7과 같다', () => {
    expect(UPLOAD_MESSAGES.STORAGE_QUOTA_EXCEEDED).toBe(
      '사진 저장 공간(1GB)을 다 썼어요. 쓰지 않는 사진이 든 글을 지우면 7일 뒤 공간이 돌아와요',
    );
    expect(UPLOAD_MESSAGES.DAILY_UPLOAD_LIMIT).toBe(
      '오늘은 사진을 200장까지 올릴 수 있어요. 내일 다시 시도해 주세요',
    );
  });
});

describe('formatBytes', () => {
  it('KB·MB·GB를 소수 한 자리까지 (끝의 .0은 뺀다)', () => {
    expect(formatBytes(0)).toBe('0KB');
    expect(formatBytes(1536)).toBe('1.5KB');
    expect(formatBytes(312 * MB)).toBe('312MB');
    expect(formatBytes(312.46 * MB)).toBe('312.5MB');
    expect(formatBytes(GB)).toBe('1GB');
    expect(formatBytes(1.25 * GB)).toBe('1.3GB');
  });
});
