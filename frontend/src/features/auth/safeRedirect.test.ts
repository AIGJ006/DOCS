import { describe, expect, it } from 'vitest';
import { isSafeRedirect, redirectFromSearch, safeRedirect } from './safeRedirect';

/** 서버 `SafeRedirectResolverTest`와 같은 표 (R-33, FR-039). */
const TABLE: Array<[string, string]> = [
  ['/', '/'],
  ['/settings', '/settings'],
  ['/settings?tab=1', '/settings?tab=1'],
  ['/@kim755030/posts/3', '/@kim755030/posts/3'],
  ['/search?q=a%2Fb', '/search?q=a%2Fb'],
  ['//evil.com', '/'],
  ['///evil.com', '/'],
  ['/\\evil.com', '/'],
  ['https://evil.com', '/'],
  ['http:/evil.com', '/'],
  ['javascript:alert(1)', '/'],
  ['evil.com', '/'],
  ['%2F%2Fevil.com', '/'],
  ['/%2F%2Fevil.com', '/'],
  ['/%5Cevil.com', '/'],
  ['/%252F%252Fevil.com', '/'],
  ['/a\\b', '/'],
  ['/a%0d%0aSet-Cookie:x', '/'],
  ['/%E0%A4%A', '/'],
];

describe('safeRedirect', () => {
  it.each(TABLE)('%s → %s', (candidate, expected) => {
    expect(safeRedirect(candidate)).toBe(expected);
  });

  it.each([null, undefined, ''])('빈 값(%s)은 /', (candidate) => {
    expect(safeRedirect(candidate)).toBe('/');
    expect(isSafeRedirect(candidate)).toBe(false);
  });

  it('제어 문자·너무 긴 주소는 /', () => {
    expect(safeRedirect('/a\tb')).toBe('/');
    expect(safeRedirect('/a\u0000b')).toBe('/');
    expect(safeRedirect('/' + 'a'.repeat(3000))).toBe('/');
  });

  it('로그인 화면 주소의 returnTo(다른 화면이 보냄)·redirect(계약 이름)를 읽는다', () => {
    expect(redirectFromSearch('?returnTo=%2Fsettings%3Ftab%3D1')).toBe('/settings?tab=1');
    expect(redirectFromSearch('?redirect=/manage/posts')).toBe('/manage/posts');
    expect(redirectFromSearch('?returnTo=//evil.com')).toBe('/');
    expect(redirectFromSearch('')).toBe('/');
  });
});
