/**
 * 화면 비밀번호 규칙 (FR-013·014). 서버 `PasswordRule`의 앞 다섯 규칙과 같은 순서·같은 판단이다.
 * 이메일 포함·흔한 비밀번호·확인 일치는 서버만 판단해 칸 오류로 돌려준다.
 */

export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 16;

/** 서버 `PasswordPolicy.SPECIAL_CHARS`와 같은 지정 특수문자 (07 §4). */
export const PASSWORD_SPECIAL_CHARS = '!@#$%^&*()-_=+[]{};:\'",.<>/?\\|`~';

export type PasswordRuleId = 'LENGTH' | 'LETTER_CASE' | 'DIGIT' | 'SPECIAL' | 'ALLOWED_CHARS';

export interface PasswordRuleState {
  id: PasswordRuleId;
  label: string;
  met: boolean;
}

const RULE_LABELS: Record<PasswordRuleId, string> = {
  LENGTH: '8~16자 (최대 16자)',
  LETTER_CASE: '영문 대문자·소문자 각 1개 이상',
  DIGIT: '숫자 1개 이상',
  SPECIAL: '특수문자 1개 이상',
  ALLOWED_CHARS: '영문·숫자·특수문자만 (공백·한글 불가)',
};

const RULE_ORDER: PasswordRuleId[] = ['LENGTH', 'LETTER_CASE', 'DIGIT', 'SPECIAL', 'ALLOWED_CHARS'];

function isAllowed(ch: string): boolean {
  return /[A-Za-z0-9]/.test(ch) || PASSWORD_SPECIAL_CHARS.includes(ch);
}

function satisfied(id: PasswordRuleId, pw: string): boolean {
  const chars = [...pw];
  switch (id) {
    case 'LENGTH':
      // 서버(Java String.length)와 같이 UTF-16 단위로 센다
      return pw.length >= PASSWORD_MIN_LENGTH && pw.length <= PASSWORD_MAX_LENGTH;
    case 'LETTER_CASE':
      return /[A-Z]/.test(pw) && /[a-z]/.test(pw);
    case 'DIGIT':
      return /[0-9]/.test(pw);
    case 'SPECIAL':
      return chars.some((ch) => PASSWORD_SPECIAL_CHARS.includes(ch));
    case 'ALLOWED_CHARS':
      // 빈 입력은 아직 아무것도 충족하지 않은 것으로 보인다
      return pw.length > 0 && chars.every(isAllowed);
  }
}

export function evaluatePassword(password: string): PasswordRuleState[] {
  return RULE_ORDER.map((id) => ({ id, label: RULE_LABELS[id], met: satisfied(id, password) }));
}
