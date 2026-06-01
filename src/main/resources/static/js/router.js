// 해시 라우터 (#102)
//
// location.hash 기반 SPA 라우터. 서버 forward 가 필요 없다. ':param' 패턴과
// not-found 폴백을 지원한다. #103~#110 이 register('/albums', ...),
// register('/albums/:id', ...) 처럼 자유롭게 라우트를 추가한다.

const routes = []; // { regex, keys, handler }
let notFoundHandler = ({ app }) => {
  app.innerHTML = '<div class="alert alert-warning">페이지를 찾을 수 없습니다.</div>';
};

/** '/albums/:id' → 정규식 + 키 이름 목록으로 컴파일. */
function compile(pattern) {
  const keys = [];
  const source =
    '^' +
    pattern.replace(/:[^/]+/g, (match) => {
      keys.push(match.slice(1));
      return '([^/]+)';
    }) +
    '$';
  return { regex: new RegExp(source), keys };
}

/** 라우트 등록. 특정 패턴을 일반 패턴보다 먼저 등록하면 우선 매칭된다. */
export function register(pattern, handler) {
  const { regex, keys } = compile(pattern);
  routes.push({ regex, keys, handler });
}

export function setNotFound(handler) {
  notFoundHandler = handler;
}

/** location.hash → '/path' (앞의 # 제거, 비어 있으면 '/'). */
function currentHash() {
  const raw = location.hash.replace(/^#/, '');
  return raw || '/';
}

function resolve() {
  const [pathPart, queryPart] = currentHash().split('?');
  const query = Object.fromEntries(new URLSearchParams(queryPart || ''));
  const app = document.getElementById('app');

  for (const route of routes) {
    const match = route.regex.exec(pathPart);
    if (match) {
      const params = {};
      route.keys.forEach((key, i) => {
        params[key] = decodeURIComponent(match[i + 1]);
      });
      return route.handler({ app, params, query });
    }
  }
  return notFoundHandler({ app, params: {}, query });
}

/** hashchange 리스너 등록 + 초기 1회 렌더. */
export function start() {
  window.addEventListener('hashchange', resolve);
  resolve();
}

/** 프로그램적 내비게이션. */
export function navigate(hash) {
  location.hash = hash.startsWith('#') ? hash : '#' + hash;
}
