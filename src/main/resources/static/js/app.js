// SPA 진입점 (#102)
//
// 부트스트랩 순서: 네비바 렌더 → 인증 변화 구독 → 라우트 등록 → 라우터 시작.
// 홈('/')은 비로그인 카탈로그 공개 조회를 시연하고, '#/login'은 DoD 검증용
// 임시 미니 로그인 폼이다(정식 인증 UI 는 #104 가 교체).

import * as api from './api.js';
import * as store from './store.js';
import * as router from './router.js';

// innerHTML 에 사용자 문자열을 넣기 전 반드시 이스케이프(#103+ 가 상속할 컨벤션).
function escapeHtml(value) {
  return String(value ?? '').replace(
    /[&<>"']/g,
    (ch) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[ch],
  );
}

// ---- 네비바 ----------------------------------------------------------------

function renderNavbar(user) {
  const slot = document.getElementById('nav-auth');
  if (!slot) return;

  if (!user) {
    slot.innerHTML = '<a class="btn btn-outline-primary btn-sm" href="#/login">로그인</a>';
    return;
  }

  slot.innerHTML = `
    ${user.isAdmin ? '<a class="nav-link d-inline px-2" href="#/admin">Admin</a>' : ''}
    <span class="navbar-text">${escapeHtml(user.email ?? '회원')}</span>
    <button id="logout-btn" type="button" class="btn btn-outline-secondary btn-sm">로그아웃</button>
  `;
  document.getElementById('logout-btn').addEventListener('click', async () => {
    await api.logoutFlow();
    router.navigate('/');
  });
}

// ---- 공통 뷰 헬퍼 ----------------------------------------------------------

function showLoading(app, message = '불러오는 중…') {
  app.innerHTML = `
    <div class="d-flex align-items-center gap-2 text-secondary">
      <div class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></div>
      <span>${escapeHtml(message)}</span>
    </div>`;
}

function showError(app, err) {
  const detail = err && err.detail ? err.detail : err && err.title ? err.title : '요청에 실패했습니다.';
  app.innerHTML = `<div class="alert alert-danger" role="alert">${escapeHtml(detail)}</div>`;
}

// ---- 홈 / 카탈로그 라우트 (비로그인 공개 조회) -----------------------------

async function renderHome({ app, isCurrent }) {
  showLoading(app, '카탈로그를 불러오는 중…');
  try {
    // auth:false → 인증 헤더 없이 공개 조회됨을 입증
    const page = await api.get('/albums', { auth: false, query: { page: 0, size: 20 } });
    if (!isCurrent()) return; // 응답 도착 전 다른 라우트로 이동 → 폐기
    const albums = (page && page.content) || [];

    if (albums.length === 0) {
      app.innerHTML = '<div class="alert alert-info">표시할 앨범이 없습니다. 데모 시드(#103)를 적용해 보세요.</div>';
      return;
    }

    const cards = albums
      .map(
        (a) => `
        <div class="col-12 col-sm-6 col-lg-4">
          <div class="card h-100 album-card">
            <div class="card-body">
              <h6 class="card-title mb-1">${escapeHtml(a.title)}</h6>
              <p class="card-subtitle text-secondary small mb-2">${escapeHtml(a.artist?.name ?? '')}</p>
              <span class="badge text-bg-light">${escapeHtml(a.format ?? '')}</span>
              <div class="mt-2 fw-semibold">${Number(a.price ?? 0).toLocaleString('ko-KR')}원</div>
            </div>
          </div>
        </div>`,
      )
      .join('');

    app.innerHTML = `
      <h1 class="h4 mb-3">카탈로그</h1>
      <div class="row g-3">${cards}</div>`;
  } catch (err) {
    if (!isCurrent()) return;
    showError(app, err);
  }
}

// ---- 임시 미니 로그인 폼 (DoD 검증용, #104 가 정식 UI 로 교체) --------------

function renderLogin({ app }) {
  if (store.isAuthenticated()) {
    router.navigate('/');
    return;
  }
  app.innerHTML = `
    <div class="row justify-content-center">
      <div class="col-12 col-md-6 col-lg-4">
        <h1 class="h5 mb-3">로그인 <span class="badge text-bg-warning">임시 · #104 교체</span></h1>
        <form id="login-form" novalidate>
          <div class="mb-3">
            <label for="email" class="form-label">이메일</label>
            <input type="email" class="form-control" id="email" autocomplete="username" required>
          </div>
          <div class="mb-3">
            <label for="password" class="form-label">비밀번호</label>
            <input type="password" class="form-control" id="password" autocomplete="current-password" required>
          </div>
          <div id="login-error" class="alert alert-danger d-none" role="alert"></div>
          <button type="submit" class="btn btn-primary w-100">로그인</button>
        </form>
      </div>
    </div>`;

  const form = document.getElementById('login-form');
  const errorBox = document.getElementById('login-error');
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    errorBox.classList.add('d-none');
    const email = document.getElementById('email').value.trim();
    const password = document.getElementById('password').value;
    const submitBtn = form.querySelector('button[type="submit"]');
    submitBtn.disabled = true;
    try {
      await api.loginFlow(email, password);
      router.navigate('/');
    } catch (err) {
      errorBox.textContent = err && err.detail ? err.detail : '로그인에 실패했습니다.';
      errorBox.classList.remove('d-none');
    } finally {
      submitBtn.disabled = false;
    }
  });
}

// ---- 관리자 자리표시 라우트 (#106 이 정식 콘솔로 교체) ---------------------
// 네비바 Admin 링크가 not-found 로 빠지지 않도록 자리표시 라우트를 둔다.
function renderAdminPlaceholder({ app }) {
  app.innerHTML = '<div class="alert alert-info">관리자 콘솔은 곧 제공됩니다 (#106).</div>';
}

// ---- 부트스트랩 ------------------------------------------------------------

renderNavbar(store.getUser());
store.subscribe(renderNavbar);

router.register('/', renderHome);
router.register('/login', renderLogin);
router.register('/admin', renderAdminPlaceholder);

router.start();
