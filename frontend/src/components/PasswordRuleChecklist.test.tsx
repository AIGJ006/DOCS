import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import PasswordRuleChecklist from './PasswordRuleChecklist';

function items() {
  return within(screen.getByRole('list', { name: '비밀번호 규칙' })).getAllByRole('listitem');
}

function marks() {
  return items().map((item) => (item.textContent ?? '').trim().charAt(0));
}

describe('PasswordRuleChecklist', () => {
  it('규칙 5개를 서버 PasswordPolicy와 같은 순서로 보이고 "최대 16자"를 적는다', () => {
    render(<PasswordRuleChecklist password="" />);
    const texts = items().map((item) => item.textContent ?? '');
    expect(texts).toHaveLength(5);
    expect(texts[0]).toContain('8~16자');
    expect(texts[0]).toContain('최대 16자');
    expect(texts[1]).toContain('영문 대문자·소문자');
    expect(texts[2]).toContain('숫자');
    expect(texts[3]).toContain('특수문자');
    expect(texts[4]).toContain('영문·숫자·특수문자만');
  });

  it('빈 입력이면 모두 ✗', () => {
    render(<PasswordRuleChecklist password="" />);
    expect(marks()).toEqual(['✗', '✗', '✗', '✗', '✗']);
  });

  it('입력이 바뀔 때마다 규칙별 ✓/✗가 글자로 바뀐다(색만으로 표시하지 않음)', () => {
    const { rerender } = render(<PasswordRuleChecklist password="abc" />);
    expect(marks()).toEqual(['✗', '✗', '✗', '✗', '✓']);

    rerender(<PasswordRuleChecklist password="abcDEF12" />);
    expect(marks()).toEqual(['✓', '✓', '✓', '✗', '✓']);

    rerender(<PasswordRuleChecklist password="Blog#2026a" />);
    expect(marks()).toEqual(['✓', '✓', '✓', '✓', '✓']);
    // 화면 읽기 프로그램도 충족 여부를 글자로 듣는다
    expect(items()[0]).toHaveTextContent('충족');
  });

  it('16자를 넘거나 공백·한글이 들어가면 해당 규칙이 ✗', () => {
    const { rerender } = render(<PasswordRuleChecklist password="Blog#2026aaaaaaaa" />);
    expect(marks()[0]).toBe('✗');

    rerender(<PasswordRuleChecklist password="Blog# 2026a" />);
    expect(marks()[4]).toBe('✗');

    rerender(<PasswordRuleChecklist password="Blog#2026가" />);
    expect(marks()[4]).toBe('✗');
    expect(items()[4]).toHaveTextContent('아직 아니에요');
  });

  it('대문자만 있거나 소문자만 있으면 영문 대소문자 규칙은 ✗', () => {
    const { rerender } = render(<PasswordRuleChecklist password="BLOG#2026" />);
    expect(marks()[1]).toBe('✗');
    rerender(<PasswordRuleChecklist password="blog#2026" />);
    expect(marks()[1]).toBe('✗');
  });
});
