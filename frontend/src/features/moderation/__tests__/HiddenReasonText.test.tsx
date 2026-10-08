import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import HiddenReasonText from '../HiddenReasonText';
import { hiddenNotice } from '../hiddenNotice';

describe('HiddenReasonText', () => {
  it('사유가 있으면 괄호 안에 사유 이름', () => {
    render(<HiddenReasonText reason="SPAM" />);
    expect(
      screen.getByText(
        '운영 정책에 따라 숨겨진 글이에요 (사유: 스팸·광고). 다른 사람에게는 보이지 않아요',
      ),
    ).toBeInTheDocument();
  });

  it('사유가 없으면 괄호 없음', () => {
    expect(hiddenNotice(null)).toBe(
      '운영 정책에 따라 숨겨진 글이에요. 다른 사람에게는 보이지 않아요',
    );
  });

  it('모르는 코드는 기타', () => {
    expect(hiddenNotice('WHATEVER')).toBe(
      '운영 정책에 따라 숨겨진 글이에요 (사유: 기타). 다른 사람에게는 보이지 않아요',
    );
  });
});
