/**
 * 본문 GIF 재생 (005 T041 자리, 003 T077 소유, FR-039, research R11).
 *
 * 서버 렌더러가 작성자 GIF를 `<a href="{원본.gif}" title="움직이는 이미지 재생" target="_blank" …><img src="{정지 장면}"></a>`
 * 로 내보낸다. 여기서는 `a[href$=".gif"] > img`를 찾아 버튼처럼 만들고, 누르면(클릭·Enter·Space) 링크 이동을 막고 `src`를
 * 원본 ↔ 정지 장면으로 바꾼다. 스크립트가 없거나 실패하면 링크가 새 탭에서 원본을 연다(US6 #5). 인라인 스크립트·
 * `innerHTML`을 쓰지 않는다. ▶ 표시는 `postDetail.css`의 `a[href$=".gif"]::after`가 그린다.
 */

const PLAY_LABEL = '움직이는 이미지 재생';
const BOUND = 'data-gif-player';

function sameOrigin(a: string, b: string): boolean {
  try {
    return new URL(a).origin === new URL(b).origin;
  } catch {
    return false;
  }
}

function toggle(img: HTMLImageElement, original: string, still: string): void {
  const playing = img.getAttribute('aria-pressed') === 'true';
  img.setAttribute('src', playing ? still : original);
  img.setAttribute('aria-pressed', playing ? 'false' : 'true');
}

export function playableGifs(container: ParentNode | null): void {
  if (!container) return;
  const images = container.querySelectorAll<HTMLImageElement>('a[href$=".gif"] > img');
  for (const img of Array.from(images)) {
    if (img.hasAttribute(BOUND)) continue;
    const link = img.parentElement as HTMLAnchorElement;
    const original = link.getAttribute('href') ?? '';
    const still = img.getAttribute('src') ?? '';
    // 우리 저장소 사진끼리만 (정지 장면과 원본은 같은 출처). 다른 곳을 가리키는 링크는 링크 그대로 둔다
    if (!original || !still || !sameOrigin(original, still)) continue;

    img.setAttribute(BOUND, '');
    img.setAttribute('role', 'button');
    img.setAttribute('aria-pressed', 'false');
    img.setAttribute('aria-label', img.getAttribute('alt')?.trim() || PLAY_LABEL);
    img.tabIndex = 0;

    img.addEventListener('click', (event) => {
      event.preventDefault();
      toggle(img, original, still);
    });
    img.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ' || event.key === 'Spacebar') {
        event.preventDefault();
        toggle(img, original, still);
      }
    });
  }
}
