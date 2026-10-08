/**
 * 대체글 권유 (003 US5, FR-033·034). 본문에서 대체글이 빈 우리 사진을 찾고, 그 자리의 대체글만 바꾼다.
 *
 * "우리 사진"은 주소의 경로가 저장 키 모양(`/images/{yyyy}/{MM}/{uuid}(_thumb)?.{ext}`)으로 끝나는 사진이다 — 화면은 공개 주소
 * 설정을 받지 않으므로 키 모양으로 판별한다(남의 사진도 같은 모양이지만 서버가 링크로 바꾸므로 권유 대상에 들어가도 해가 없다).
 * 코드 블록·인라인 코드 안의 글자는 사진이 아니다. 외부 사진·`local:`(업로드 대기) 사진은 뺀다.
 */

export interface MissingAlt {
  /** 본문 속 우리 사진 중 몇 번째인가 (0부터). 대체글을 고쳐도 바뀌지 않아 입력 중에도 같은 사진을 가리킨다 */
  ordinal: number;
  /** `![` 시작 위치 */
  start: number;
  /** `](` 위치 (대체글 끝) */
  altEnd: number;
  url: string;
}

const OUR_IMAGE =
  /\/images\/\d{4}\/\d{2}\/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(_thumb)?\.(jpg|jpeg|png|gif|webp)$/;

/** 코드 블록(``` ·~~~)과 인라인 코드 범위. */
function codeRanges(md: string): [number, number][] {
  const ranges: [number, number][] = [];
  const fence = /^( {0,3})(`{3,}|~{3,})[^\n]*\n[\s\S]*?(?:^\1?\2[`~]*[ \t]*$|$(?![\s\S]))/gm;
  for (const m of md.matchAll(fence)) {
    ranges.push([m.index!, m.index! + m[0].length]);
  }
  const inline = /(`+)[^`]*?\1/g;
  for (const m of md.matchAll(inline)) {
    const at = m.index!;
    if (!ranges.some(([s, e]) => at >= s && at < e)) {
      ranges.push([at, at + m[0].length]);
    }
  }
  return ranges;
}

const IMAGE = /!\[((?:\\.|[^\]\\])*)\]\(\s*<?([^)\s>]+)>?(?:\s+"[^"]*")?\s*\)/g;

interface OurImage extends MissingAlt {
  alt: string;
}

function isOurs(url: string): boolean {
  if (!/^https?:/.test(url)) return false;
  try {
    return OUR_IMAGE.test(new URL(url).pathname);
  } catch {
    return false;
  }
}

/** 본문의 우리 사진 전부 (코드 안 제외). */
function ourImages(md: string): OurImage[] {
  const code = codeRanges(md);
  const result: OurImage[] = [];
  for (const m of md.matchAll(IMAGE)) {
    const start = m.index!;
    if (code.some(([s, e]) => start >= s && start < e)) continue;
    const [, alt, url] = m;
    if (!isOurs(url)) continue;
    result.push({ ordinal: result.length, start, altEnd: start + 2 + alt.length, url, alt });
  }
  return result;
}

export function findMissingAlt(md: string): MissingAlt[] {
  return ourImages(md)
    .filter((image) => image.alt.trim() === '')
    .map(({ ordinal, start, altEnd, url }) => ({ ordinal, start, altEnd, url }));
}

function escapeAlt(text: string): string {
  return text
    .replace(/[\r\n]+/g, ' ')
    .replace(/\\/g, '\\\\')
    .replace(/\]/g, '\\]');
}

/** 그 사진(`ordinal`)의 대체글을 `text`로 바꾼 본문. 사진이 없어졌으면 그대로. */
export function setAlt(md: string, target: Pick<MissingAlt, 'ordinal'>, text: string): string {
  const image = ourImages(md)[target.ordinal];
  if (!image) return md;
  return md.slice(0, image.start + 2) + escapeAlt(text) + md.slice(image.altEnd);
}
