import type { NotificationItem } from '../../api/types/notification';
import { reasonLabel } from '../moderation/reasonLabels';

/**
 * 알림 문장 조립 (011 research R16, 25 §2). 서버는 완성 문장을 주지 않는다 — 닉네임·제목이 바뀌어도 문구 규칙은 여기 한 곳.
 *
 * 문장은 조각 목록으로 돌려준다. `strong`인 조각(닉네임)만 굵게 그린다. 모든 값은 텍스트로만 그린다(HTML로 해석하지 않음).
 *
 * - 탈퇴한 사람은 닉네임 자리에 "탈퇴한 사용자"(굵게 하지 않음). 조사는 "님이" 대신 "가" — "탈퇴한 사용자가 …",
 *   묶음은 "탈퇴한 사용자 외 N명이 …".
 * - 받는 사람이 글을 읽을 수 없으면 제목·미리보기 없이 동사 뒤에 " · 볼 수 없는 글이에요"를 붙인다.
 */
export interface TextSegment {
  text: string;
  strong?: boolean;
}

export const UNAVAILABLE_POST_TEXT = '볼 수 없는 글이에요';
export const WITHDRAWN_ACTOR_TEXT = '탈퇴한 사용자';

/** "김민서님이" · "김민서님 외 3명이" · "탈퇴한 사용자가" · "탈퇴한 사용자 외 3명이" */
function subject(item: NotificationItem): TextSegment[] {
  const actor = item.actor;
  const withdrawn = !actor || 'withdrawn' in actor;
  const name: TextSegment = withdrawn
    ? { text: WITHDRAWN_ACTOR_TEXT }
    : { text: actor.nickname, strong: true };
  if (item.othersCount > 0) {
    return [name, { text: `${withdrawn ? '' : '님'} 외 ${item.othersCount}명이 ` }];
  }
  return [name, { text: withdrawn ? '가 ' : '님이 ' }];
}

function title(item: NotificationItem): string | null {
  return item.post && 'title' in item.post ? item.post.title : null;
}

function preview(item: NotificationItem): string {
  return item.comment ? `: "${item.comment.preview}"` : '';
}

const UNAVAILABLE_SUFFIX = ` · ${UNAVAILABLE_POST_TEXT}`;

export function notificationSegments(item: NotificationItem): TextSegment[] {
  const t = title(item);
  switch (item.type) {
    case 'COMMENT':
      return [
        ...subject(item),
        {
          text:
            t === null
              ? `댓글을 남겼어요${UNAVAILABLE_SUFFIX}`
              : `「${t}」에 댓글을 남겼어요${preview(item)}`,
        },
      ];
    case 'REPLY':
      return [
        ...subject(item),
        {
          text:
            t === null
              ? `회원님의 댓글에 답글을 남겼어요${UNAVAILABLE_SUFFIX}`
              : `회원님의 댓글에 답글을 남겼어요${preview(item)}`,
        },
      ];
    case 'LIKE':
      return [
        ...subject(item),
        { text: t === null ? `글을 좋아해요${UNAVAILABLE_SUFFIX}` : `「${t}」을 좋아해요` },
      ];
    case 'FOLLOW':
      return [...subject(item), { text: '회원님을 팔로우해요' }];
    case 'NEW_POST':
      return [
        ...subject(item),
        {
          text: t === null ? `새 글을 올렸어요${UNAVAILABLE_SUFFIX}` : `새 글을 올렸어요: 「${t}」`,
        },
      ];
    case 'REPORT_RESOLVED':
      return [
        {
          text:
            item.report?.result === 'ACTION_TAKEN'
              ? '신고하신 내용을 검토해 조치했어요. 알려 주셔서 고마워요'
              : '신고하신 내용을 검토했지만 운영 정책 위반은 아니었어요',
        },
      ];
    case 'CONTENT_HIDDEN': {
      const hidden = item.hidden;
      const target =
        hidden?.targetType === 'COMMENT'
          ? '회원님의 댓글이'
          : `회원님의 글「${t ?? UNAVAILABLE_POST_TEXT}」이(가)`;
      const tail = hidden?.stillHidden
        ? `숨겨졌어요 (사유: ${reasonLabel(hidden.reason)})`
        : '숨겨졌었어요 (지금은 다시 보여요)';
      return [{ text: `${target} 운영 정책에 따라 ${tail}` }];
    }
    default:
      return [{ text: '새 알림이 있어요' }];
  }
}

export function notificationPlainText(item: NotificationItem): string {
  return notificationSegments(item)
    .map((segment) => segment.text)
    .join('');
}
