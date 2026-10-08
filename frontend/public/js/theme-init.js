// 016 첫 화면 테마 결정. 첫 그리기 전에 <html> data-theme·data-theme-choice를 정한다.
// 인라인 스크립트는 CSP가 막아 우리 사이트 파일로 둔다. 모듈 아님, defer·async 없음.
(function () {
  var choice = 'system';
  try {
    var saved = localStorage.getItem('theme');
    if (saved === 'light' || saved === 'dark') choice = saved;
  } catch (e) {}
  var dark =
    choice === 'dark' ||
    (choice === 'system' &&
      !!window.matchMedia &&
      window.matchMedia('(prefers-color-scheme: dark)').matches);
  var root = document.documentElement;
  root.dataset.theme = dark ? 'dark' : 'light';
  root.dataset.themeChoice = choice;
})();
