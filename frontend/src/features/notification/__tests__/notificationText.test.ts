import { describe, expect, it } from 'vitest';
import { notificationPlainText, notificationSegments } from '../notificationText';
import { notification } from './fixtures';

const POST = { title: '첫 글', url: '/@na_ms/posts/7' };

/** 알림 문장 (011 T035·T040·T044, research R16, 25 §2). */
describe('notificationText', () => {
  it('하나짜리 문장 다섯 가지', () => {
    expect(notificationPlainText(notification())).toBe(
      '김민서님이 「첫 글」에 댓글을 남겼어요: "좋은 글이에요"',
    );
    expect(notificationPlainText(notification({ type: 'REPLY' }))).toBe(
      '김민서님이 회원님의 댓글에 답글을 남겼어요: "좋은 글이에요"',
    );
    expect(
      notificationPlainText(notification({ type: 'LIKE', comment: null, url: POST.url })),
    ).toBe('김민서님이 「첫 글」을 좋아해요');
    expect(
      notificationPlainText(
        notification({ type: 'FOLLOW', post: null, comment: null, url: '/@na_ms/followers' }),
      ),
    ).toBe('김민서님이 회원님을 팔로우해요');
    expect(
      notificationPlainText(notification({ type: 'NEW_POST', comment: null, url: POST.url })),
    ).toBe('김민서님이 새 글을 올렸어요: 「첫 글」');
  });

  it('닉네임만 굵게 한다', () => {
    const segments = notificationSegments(notification({ type: 'LIKE', comment: null }));
    expect(segments.filter((s) => s.strong).map((s) => s.text)).toEqual(['김민서']);
  });

  it('묶음 문장: 외 N명', () => {
    expect(
      notificationPlainText(notification({ type: 'LIKE', comment: null, othersCount: 3 })),
    ).toBe('김민서님 외 3명이 「첫 글」을 좋아해요');
    expect(
      notificationPlainText(
        notification({ type: 'FOLLOW', post: null, comment: null, othersCount: 2 }),
      ),
    ).toBe('김민서님 외 2명이 회원님을 팔로우해요');
  });

  it('othersCount 0이면 하나짜리 문장', () => {
    expect(
      notificationPlainText(notification({ type: 'LIKE', comment: null, othersCount: 0 })),
    ).toBe('김민서님이 「첫 글」을 좋아해요');
  });

  it('볼 수 없는 글은 제목·미리보기 없이 "볼 수 없는 글이에요"', () => {
    const text = notificationPlainText(
      notification({ post: { unavailable: true }, comment: null, url: null }),
    );
    expect(text).toContain('볼 수 없는 글이에요');
    expect(text).not.toContain('첫 글');
    expect(text).not.toContain('좋은 글이에요');
    expect(
      notificationPlainText(
        notification({ type: 'LIKE', post: { unavailable: true }, comment: null, url: null }),
      ),
    ).toContain('볼 수 없는 글이에요');
  });

  it('탈퇴한 사람은 "탈퇴한 사용자"로, 굵게 하지 않는다', () => {
    const item = notification({ type: 'LIKE', comment: null, actor: { withdrawn: true } });
    expect(notificationPlainText(item)).toBe('탈퇴한 사용자가 「첫 글」을 좋아해요');
    expect(notificationSegments(item).some((s) => s.strong)).toBe(false);
    expect(notificationPlainText({ ...item, othersCount: 2 })).toBe(
      '탈퇴한 사용자 외 2명이 「첫 글」을 좋아해요',
    );
  });

  it('신고 결과 두 문장', () => {
    const base = { actor: null, post: null, comment: null, url: null };
    expect(
      notificationPlainText(
        notification({ ...base, type: 'REPORT_RESOLVED', report: { result: 'ACTION_TAKEN' } }),
      ),
    ).toBe('신고하신 내용을 검토해 조치했어요. 알려 주셔서 고마워요');
    expect(
      notificationPlainText(
        notification({ ...base, type: 'REPORT_RESOLVED', report: { result: 'NO_VIOLATION' } }),
      ),
    ).toBe('신고하신 내용을 검토했지만 운영 정책 위반은 아니었어요');
  });

  it('글 숨김과 해제', () => {
    const base = { type: 'CONTENT_HIDDEN' as const, actor: null, comment: null };
    expect(
      notificationPlainText(
        notification({
          ...base,
          hidden: { targetType: 'POST', stillHidden: true, reason: 'SPAM' },
        }),
      ),
    ).toBe('회원님의 글「첫 글」이(가) 운영 정책에 따라 숨겨졌어요 (사유: 스팸·광고)');
    expect(
      notificationPlainText(
        notification({
          ...base,
          hidden: { targetType: 'POST', stillHidden: false, reason: null },
        }),
      ),
    ).toBe('회원님의 글「첫 글」이(가) 운영 정책에 따라 숨겨졌었어요 (지금은 다시 보여요)');
  });

  it('댓글 숨김은 글 제목 없이', () => {
    const base = { type: 'CONTENT_HIDDEN' as const, actor: null, comment: null, post: null };
    const hidden = notificationPlainText(
      notification({
        ...base,
        hidden: { targetType: 'COMMENT', stillHidden: true, reason: 'ABUSE' },
      }),
    );
    expect(hidden).toBe('회원님의 댓글이 운영 정책에 따라 숨겨졌어요 (사유: 욕설·혐오)');
    expect(
      notificationPlainText(
        notification({
          ...base,
          hidden: { targetType: 'COMMENT', stillHidden: false, reason: null },
        }),
      ),
    ).toBe('회원님의 댓글이 운영 정책에 따라 숨겨졌었어요 (지금은 다시 보여요)');
  });
});
