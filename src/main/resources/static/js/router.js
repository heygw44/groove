// 해시 라우터 (#102)
//
// location.hash 기반 SPA 라우터. 서버 forward 가 필요 없다. ':param' 패턴과
// not-found 폴백을 지원한다. #103~#110 이 register('/albums', ...),
// register('/albums/:id', ...) 처럼 자유롭게 라우트를 추가한다.
//
// 핸들러는 { app, params, query, isCurrent } 를 받는다. isCurrent() 는 비동기
// 렌더 직전·직후에 호출해, 응답이 도착하기 전 다른 라우트로 이동했으면 DOM 을
// 덮어쓰지 않도록 한다(stale-response 가드). 모든 비동기 핸들러는 await 뒤에
// `if (!isCurrent()) return;` 를 둘 것.

const routes = []; // { regex, keys, handler }
let notFoundHandler = ({ app }) => {
  app.innerHTML = '<div class="alert alert-warning">페이지를 찾을 수 없습니다.</div>';
};

// 네비게이션 세대 토큰. resolve() 마다 증가시켜, 핸들러가 자신이 여전히 현재
// 라우트인지 isCurrent() 로 확인할 수 있게 한다(같은 hash 로 재이동해도 안전).
let navToken = 0;

/** 정규식 메타문자를 리터럴로 이스케이프. */
function escapeRegex(literal) {
  return literal.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/** '/albums/:id' → 정규식 + 키 이름 목록으로 컴파일. 리터럴 구간은 이스케이프하고 :param 만 캡처. */
function compile(pattern) {
  const keys = [];
  const source =
    '^' +
    escapeRegex(pattern).replace(/:([A-Za-z0-9_]+)/g, (_, name) => {
      keys.push(name);
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
  const token = ++navToken;
  const isCurrent = () => token === navToken;
  const [pathPart, queryPart] = currentHash().split('?');
  const query = Object.fromEntries(new URLSearchParams(queryPart || ''));
  const app = document.getElementById('app');

  for (const route of routes) {
    const match = route.regex.exec(pathPart);
    if (match) {
      const params = {};
      route.keys.forEach((key, i) => {
        const raw = match[i + 1];
        try {
          params[key] = decodeURIComponent(raw);
        } catch {
          params[key] = raw; // 잘못된 % 인코딩 → 원문 유지(URIError 로 라우터가 멈추지 않도록)
        }
      });
      return route.handler({ app, params, query, isCurrent });
    }
  }
  return notFoundHandler({ app, params: {}, query, isCurrent });
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
