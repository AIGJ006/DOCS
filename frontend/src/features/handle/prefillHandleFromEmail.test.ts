import { describe, expect, it } from 'vitest';
import { bodyFromEmail, prefillHandleFromEmail } from './prefillHandleFromEmail';

/**
 * 08 §3 ①~⑧ — 서버 `HandleSuggesterTest`(bodyFromEmail·examples)와 같은 데이터. ⑨ 접두어·⑩ 중복 번호는 서버 `suggestion`이
 * 맡는다(R-15).
 */
const SHARED: [string, string | null][] = [
  ['kim755030@naver.com', 'kim755030'],
  ['kim755030@daum.net', 'kim755030'],
  ['gokim@naver.com', 'gokim'],
  ['Kim.Min-Seo+blog@naver.com', 'kim_min_seo'],
  ['_kim__min_@x.com', 'kim_min'],
  ['admin@x.com', 'admin'],
  ['ab@x.com', null],
  ['김민서@한국.kr', null],
  ['12345678+octocat@users.noreply.github.com', '12345678'],
  ['kim+blog@naver.com', 'kim'],
  ['abcdefghijklmnopqrstuvwxyz123.4567@x.com', 'abcdefghijklmnopqrstuvwxyz123'],
  ['abcdefghijklmnopqrstuvwxyz1234567@x.com', 'abcdefghijklmnopqrstuvwxyz1234'],
  ['no-at-sign', 'no_at_sign'],
];

describe('bodyFromEmail (①~⑦)', () => {
  it.each(SHARED)('%s → %s', (email, expected) => {
    expect(bodyFromEmail(email)).toBe(expected);
  });

  it('대문자·-가 생기지 않는다', () => {
    for (const email of [
      'Kim.Min-Seo@x.com',
      'A-B-C-D@x.com',
      'UPPER.CASE@x.com',
      'x--y..z@x.com',
    ]) {
      const body = bodyFromEmail(email) ?? '';
      expect(body).not.toContain('-');
      expect(body).toBe(body.toLowerCase());
    }
  });
});

describe('prefillHandleFromEmail (⑧ 포함)', () => {
  it('3자 이상이면 본문 그대로', () => {
    expect(prefillHandleFromEmail('Kim.Min-Seo+blog@naver.com')).toBe('kim_min_seo');
  });

  it('3자 미만이면 user_ + 6자리 난수', () => {
    expect(prefillHandleFromEmail('ab@x.com')).toMatch(/^user_\d{6}$/);
    expect(prefillHandleFromEmail('김민서@한국.kr')).toMatch(/^user_\d{6}$/);
  });

  it('빈 이메일은 빈 값', () => {
    expect(prefillHandleFromEmail('   ')).toBe('');
  });
});
