import { beforeEach, describe, expect, it } from 'vitest';
import { playableGifs } from '../gifPlayer';

const ORIGINAL = 'https://cdn.example/blog/images/2026/10/a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d.gif';
const THUMB =
  'https://cdn.example/blog/images/2026/10/a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d_thumb.jpg';

function container(html: string): HTMLElement {
  const div = document.createElement('div');
  div.innerHTML = html;
  document.body.replaceChildren(div);
  return div;
}

function gifHtml(alt: string): string {
  return `<p><a rel="noopener noreferrer nofollow ugc" href="${ORIGINAL}" title="움직이는 이미지 재생" target="_blank"><img src="${THUMB}" alt="${alt}" loading="lazy" decoding="async"></a></p>`;
}

function key(target: Element, name: string): KeyboardEvent {
  const event = new KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true });
  target.dispatchEvent(event);
  return event;
}

describe('playableGifs (003 T072, FR-039)', () => {
  beforeEach(() => {
    document.body.replaceChildren();
  });

  it('GIF 링크 안 이미지에 버튼 속성을 붙인다 (대체글이 이름)', () => {
    const root = container(gifHtml('춤추는 고양이'));
    playableGifs(root);
    const img = root.querySelector('img')!;
    expect(img.getAttribute('role')).toBe('button');
    expect(img.getAttribute('aria-pressed')).toBe('false');
    expect(img.getAttribute('aria-label')).toBe('춤추는 고양이');
    expect(img.tabIndex).toBe(0);
  });

  it('대체글이 비면 "움직이는 이미지 재생"', () => {
    const root = container(gifHtml(''));
    playableGifs(root);
    expect(root.querySelector('img')!.getAttribute('aria-label')).toBe('움직이는 이미지 재생');
  });

  it('클릭하면 원본으로 재생하고 다시 누르면 정지 장면, 링크 이동은 막는다', () => {
    const root = container(gifHtml('a'));
    playableGifs(root);
    const img = root.querySelector('img')!;

    const first = new MouseEvent('click', { bubbles: true, cancelable: true });
    img.dispatchEvent(first);
    expect(first.defaultPrevented).toBe(true);
    expect(img.getAttribute('src')).toBe(ORIGINAL);
    expect(img.getAttribute('aria-pressed')).toBe('true');

    img.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    expect(img.getAttribute('src')).toBe(THUMB);
    expect(img.getAttribute('aria-pressed')).toBe('false');
  });

  it('Enter·Space로도 바뀌고 기본 동작을 막는다, 다른 키는 그대로', () => {
    const root = container(gifHtml('a'));
    playableGifs(root);
    const img = root.querySelector('img')!;

    expect(key(img, 'Enter').defaultPrevented).toBe(true);
    expect(img.getAttribute('src')).toBe(ORIGINAL);
    expect(key(img, ' ').defaultPrevented).toBe(true);
    expect(img.getAttribute('src')).toBe(THUMB);
    expect(key(img, 'a').defaultPrevented).toBe(false);
    expect(img.getAttribute('src')).toBe(THUMB);
  });

  it('GIF가 아닌 링크·링크 밖 GIF·다른 출처를 가리키는 링크는 건드리지 않는다', () => {
    const root = container(
      `<a href="https://cdn.example/x.png"><img src="${THUMB}" alt="p"></a>` +
        `<img src="${ORIGINAL}" alt="bare">` +
        `<a href="https://other.example/y.gif"><img src="${THUMB}" alt="o"></a>`,
    );
    playableGifs(root);
    for (const img of Array.from(root.querySelectorAll('img'))) {
      expect(img.hasAttribute('role')).toBe(false);
    }
  });

  it('두 번 불러도 한 번만 등록된다', () => {
    const root = container(gifHtml('a'));
    playableGifs(root);
    playableGifs(root);
    const img = root.querySelector('img')!;
    img.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));
    expect(img.getAttribute('src')).toBe(ORIGINAL);
  });

  it('container가 없으면 아무 것도 하지 않는다', () => {
    expect(() => playableGifs(null)).not.toThrow();
  });
});
