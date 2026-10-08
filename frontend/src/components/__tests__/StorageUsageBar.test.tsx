import { render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetClientForTests } from '../../api/client';
import { errorBody, json, stubFetch } from '../../test/fetchRoutes';
import StorageUsageBar from '../StorageUsageBar';

const MB = 1_048_576;
const GB = 1_073_741_824;

function body(usedBytes: number) {
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

beforeEach(() => resetClientForTests());
afterEach(() => vi.unstubAllGlobals());

describe('StorageUsageBar', () => {
  it('사용량 글자·막대 비율·돌아오는 시점 안내', async () => {
    stubFetch({ 'GET /api/me/storage': () => json(200, body(312 * MB)) });
    render(<StorageUsageBar />);

    expect(await screen.findByText('사진 저장 공간 312MB / 1GB')).toBeInTheDocument();
    const bar = screen.getByRole('progressbar', { name: '사진 저장 공간' });
    expect(bar).toHaveAttribute('aria-valuenow', '30');
    expect(bar).toHaveAttribute('aria-valuemax', '100');
    expect(screen.getByText('지운 사진의 공간은 7일 뒤 돌아와요')).toBeInTheDocument();
  });

  it('받은 값을 그대로 쓸 수 있다 (다시 부르지 않음)', () => {
    const fetchMock = stubFetch({});
    render(<StorageUsageBar usage={body(GB)} />);
    expect(screen.getByText('사진 저장 공간 1GB / 1GB')).toBeInTheDocument();
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('불러오지 못하면 막대 대신 안내', async () => {
    stubFetch({
      'GET /api/me/storage': () => json(503, errorBody('TEMPORARILY_UNAVAILABLE', 'x')),
    });
    render(<StorageUsageBar />);
    expect(await screen.findByText('사진 저장 공간을 불러오지 못했어요')).toBeInTheDocument();
    expect(screen.queryByRole('progressbar')).toBeNull();
  });
});
