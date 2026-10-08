import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import LastActiveBadge from './LastActiveBadge';
import { lastActiveText } from '../features/friends/lastActiveText';

describe('LastActiveBadge (FR-060)', () => {
  it.each([
    [{ bucket: 'TODAY' }, '오늘'],
    [{ bucket: 'YESTERDAY' }, '어제'],
    [{ bucket: 'DAYS_AGO', days: 3 }, '3일 전'],
    [{ bucket: 'OVER_A_WEEK' }, '1주 이상'],
  ])('%o → %s', (value, text) => {
    expect(lastActiveText(value)).toBe(text);
    render(<LastActiveBadge value={value} />);
    expect(screen.getByText(`최근 활동 ${text}`)).toBeInTheDocument();
  });

  it('값이 없으면 아무것도 그리지 않는다', () => {
    const { container } = render(<LastActiveBadge value={undefined} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('알 수 없는 구간도 그리지 않는다', () => {
    const { container } = render(<LastActiveBadge value={{ bucket: 'NOPE' }} />);
    expect(container).toBeEmptyDOMElement();
  });
});
