const INITIALS = 'ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ';
const MEDIALS = 'ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ';
const FINALS = ['', ...'ㄱㄲㄳㄴㄵㄶㄷㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅄㅅㅆㅇㅈㅊㅋㅌㅍㅎ'];

/** 두벌식 자판에서 같은 자리의 영문 키 (겹받침·겹모음은 두 키). 쌍자음·ㅒ·ㅖ는 Shift 키라 소문자로. */
const DUBEOLSIK: Record<string, string> = {
  ㄱ: 'r',
  ㄲ: 'r',
  ㄴ: 's',
  ㄷ: 'e',
  ㄸ: 'e',
  ㄹ: 'f',
  ㅁ: 'a',
  ㅂ: 'q',
  ㅃ: 'q',
  ㅅ: 't',
  ㅆ: 't',
  ㅇ: 'd',
  ㅈ: 'w',
  ㅉ: 'w',
  ㅊ: 'c',
  ㅋ: 'z',
  ㅌ: 'x',
  ㅍ: 'v',
  ㅎ: 'g',
  ㅏ: 'k',
  ㅐ: 'o',
  ㅑ: 'i',
  ㅒ: 'o',
  ㅓ: 'j',
  ㅔ: 'p',
  ㅕ: 'u',
  ㅖ: 'p',
  ㅗ: 'h',
  ㅛ: 'y',
  ㅜ: 'n',
  ㅠ: 'b',
  ㅡ: 'm',
  ㅣ: 'l',
  ㅘ: 'hk',
  ㅙ: 'ho',
  ㅚ: 'hl',
  ㅝ: 'nj',
  ㅞ: 'np',
  ㅟ: 'nl',
  ㅢ: 'ml',
  ㄳ: 'rt',
  ㄵ: 'sw',
  ㄶ: 'sg',
  ㄺ: 'fr',
  ㄻ: 'fa',
  ㄼ: 'fq',
  ㄽ: 'ft',
  ㄾ: 'fx',
  ㄿ: 'fv',
  ㅀ: 'fg',
  ㅄ: 'qt',
};

function hangulToKeys(char: string): string {
  const code = char.charCodeAt(0);
  if (code >= 0xac00 && code <= 0xd7a3) {
    const offset = code - 0xac00;
    const initial = INITIALS[Math.floor(offset / 588)] ?? '';
    const medial = MEDIALS[Math.floor((offset % 588) / 28)] ?? '';
    const final = FINALS[offset % 28] ?? '';
    return [initial, medial, final].map((jamo) => DUBEOLSIK[jamo] ?? '').join('');
  }
  return DUBEOLSIK[char] ?? char;
}

/**
 * 주소 칸 입력 정리 (FR-016·018): 한글 자판 상태로 친 글자는 같은 자리 영문으로, 대문자는 소문자로, `-`와 허용되지 않는 글자는
 * 버린다. 형식 전체(길이·처음·끝)는 서버가 판정한다.
 */
export function toHandleChars(raw: string): string {
  return Array.from(raw)
    .map(hangulToKeys)
    .join('')
    .toLowerCase()
    .replace(/[^a-z0-9_]/g, '');
}
