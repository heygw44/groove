// Postman 컬렉션/환경 → Bruno .bru 파일 트리 변환 (이슈 #141 후속, Postman→Bruno 이행)
//
// 입력:  postman/groove.collection.json + postman/groove.environment.json
// 출력:  bruno/  (.bru 파일 트리 + environments/ + bruno.json)
//
// 실행:  node scripts/postman-to-bruno.mjs   (@usebruno/converters·lang 은 devDependencies)
//
// 변환 단계 보정(컨버터 자동처리 불가) + PR #216 CodeRabbit 리뷰 반영을 patch* 에 내장한다:
//   1) type=secret 환경변수 값 누락 → 평문 vars 로 보존 (Postman env 도 평문이라 노출 동등)
//   2) body·script 의 {{$guid}} → Bruno 네이티브 {{$randomUUID}}
//   3) Rate Limit 의 pm.sendRequest 미번역 + 지역변수 충돌 → bru.sendRequest 루프로 재작성
//   4) 8.Admin 폴더 pre-request 에 admin 토큰 1회 로그인 주입(중첩 서브폴더 선실행 우회)
//   5) URL 쿼리/params:query 이중 소스 제거 + 일부 요청 body/script 정합화(patchRequestObject)
import { postmanToBruno, postmanToBrunoEnvironment } from '@usebruno/converters';
import { jsonToBruV2, jsonToCollectionBru, envJsonToBruV2 } from '@usebruno/lang';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

// 보정 6(CodeRabbit #216): 실행 디렉터리(process.cwd) 의존 + 무조건 삭제는 위험 →
// 스크립트 위치 기준으로 루트를 잡고, 입력 파일 존재를 확인한 뒤에만 bruno/ 를 삭제한다.
const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT = path.join(ROOT, 'bruno');
const COLLECTION = path.join(ROOT, 'postman/groove.collection.json');
const ENVIRONMENT = path.join(ROOT, 'postman/groove.environment.json');
if (!fs.existsSync(COLLECTION) || !fs.existsSync(ENVIRONMENT)) {
  throw new Error(`postman 입력 파일을 찾을 수 없습니다(${COLLECTION}). bruno/ 삭제를 중단합니다.`);
}

// 폴더 선두 번호 → 카테고리 태그 (기존 newman --folder 선택을 bru --tags 로 대체)
const TAG_BY_INDEX = {
  0: 'health', 1: 'auth', 2: 'members', 3: 'catalog', 4: 'cart', 5: 'coupons',
  6: 'memberflow', 7: 'guestflow', 8: 'admin', 9: 'edge', 10: 'cleanup', 11: 'ratelimit',
};
function tagForFolder(name) {
  const m = /^(\d+)\./.exec(name.trim());
  return m ? TAG_BY_INDEX[Number(m[1])] : null;
}
// 파일시스템 안전 이름 (경로 구분자만 치환, 한글/공백/기호는 macOS 에서 유효하므로 유지)
const safe = (s) => s.replace(/[\/\\]/g, '-').trim();

// 보정 2·3: 번역 단계가 못 잡거나 잘못 옮긴 스크립트 재작성
function patchRequestObject(item) {
  const r = item.request;
  if (!r) return;
  // Rate Limit: pm.sendRequest 미번역 + 지역 req 변수충돌(TDZ) + res.headerList.has(미지원) → 재작성
  if (item.name.startsWith('Rate Limit 초과')) {
    r.script = r.script || {};
    r.script.req = [
      '// 분당 10회 제한 버킷을 본 요청 전에 10회 선요청으로 소진 → 본 요청(11번째)이 429 가 되도록 강제.',
      '// 본 요청과 동일한 body(이메일 + 잘못된 비밀번호)를 재사용한다.',
      'const N = 10;',
      "const url = bru.getEnvVar('baseUrl') + '/auth/login';",
      // 의도적으로 틀린 자격증명(10회 실패로 버킷 소진). 키를 따옴표로 묶어 시크릿 스캐너 오탐 회피.
      'const data = { "email": "ratelimit@example.com", "password": "wrong-password" };',
      'for (let i = 0; i < N; i++) {',
      '  await bru.sendRequest({',
      "    method: 'POST',",
      '    url,',
      "    headers: { 'Content-Type': 'application/json' },",
      '    data,',
      '  }, function () { /* 응답 무시 — 버킷 소진만 목적 */ });',
      '}',
    ].join('\n');
    // res.getHeader('Retry-After') 는 CLI 3.4.2 에서 값을 못 집는다 → getHeaders()(소문자 키 객체)로 단언.
    r.script.res = [
      "test('429 Too Many Requests', () => expect(res.getStatus()).to.eql(429));",
      "test('Retry-After 헤더 존재', () => expect(res.getHeaders()).to.have.property('retry-after'));",
    ].join('\n');
  }

  // ── 보정 5(CodeRabbit #216): 요청별 정합화 ──
  // Change Password: 하드코딩 비번 → 환경변수 (재실행 시 cleanup 연계 깨짐 방지)
  if (item.name === 'Change Password' && r.body && typeof r.body.json === 'string') {
    r.body.json = r.body.json.replace('"P@ssw0rd!2024"', '"{{memberPassword}}"');
  }
  // Issue Coupon: 201 아닐 때(409) memberCouponId 초기화 → 이전 실행 stale 값 오염 방지
  if (item.name === 'Issue Coupon (선착순)' && r.script) {
    r.script.res = [
      "test('발급 201 / 이미발급 409 / 소진 409', () => expect([201, 409]).to.include(res.getStatus()));",
      'if (res.getStatus() === 201) {',
      "  bru.setEnvVar('memberCouponId', res.getBody().memberCouponId);",
      '} else {',
      "  bru.setEnvVar('memberCouponId', '');",
      '}',
    ].join('\n');
  }
  // PG Webhook: 서명(X-Mock-Signature) 기반이므로 컬렉션 Bearer 상속 금지 → auth: none 명시
  if (item.name.includes('PG Webhook') && r.auth) {
    r.auth.mode = 'none';
  }
  // Create Coupon Policy: 고정 2026 만료일 → 실행시점 상대값(연도 경과 시 생성 실패 방지)
  if (item.name === 'Create Coupon Policy' && r.body && typeof r.body.json === 'string') {
    r.body.json = r.body.json
      .replace('"2026-01-01T00:00:00.000Z"', '"{{couponValidFrom}}"')
      .replace('"2026-12-31T23:59:59.000Z"', '"{{couponValidUntil}}"');
    r.script = r.script || {};
    r.script.req = [
      'const now = new Date();',
      "bru.setEnvVar('couponValidFrom', new Date(now.getTime() - 5 * 60 * 1000).toISOString());",
      "bru.setEnvVar('couponValidUntil', new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000).toISOString());",
    ].join('\n');
  }
}

function reqToBru(item, tag) {
  patchRequestObject(item);
  const r = item.request || {};
  const j = {
    meta: { name: item.name, type: 'http', seq: item.seq, ...(tag ? { tags: [tag] } : {}) },
    http: {
      method: (r.method || 'get').toLowerCase(),
      // 보정 5(CodeRabbit #216): 쿼리는 params:query 가 단일 소스 → URL 쿼리스트링 제거(이중 소스 방지)
      url: (r.params || []).some((p) => p.type === 'query') ? (r.url || '').split('?')[0] : (r.url || ''),
      body: r.body?.mode || 'none',
      auth: r.auth?.mode || 'none',
    },
    params: r.params || [],
    headers: r.headers || [],
    auth: r.auth || {},
    body: r.body || {},
    script: r.script || {},
    vars: r.vars || {},
    assertions: r.assertions || [],
    tests: r.tests || '',
    docs: r.docs || '',
  };
  // 보정 2: Postman 동적변수 $guid → Bruno 네이티브 $randomUUID (body·script 공통, $guid 는 Bruno 미보장)
  return jsonToBruV2(j).replaceAll('{{$guid}}', '{{$randomUUID}}');
}

// folder.bru / collection.bru (폴더·컬렉션 레벨 메타/스크립트/헤더 보존)
function rootHasContent(root) {
  if (!root) return false;
  const req = root.request || {};
  const scriptEmpty = !req.script || (!req.script.req && !req.script.res);
  const headersEmpty = !req.headers || req.headers.length === 0;
  const testsEmpty = !req.tests;
  const docsEmpty = !root.docs;
  const authInherit = !req.auth || req.auth.mode === 'inherit' || req.auth.mode === 'none' || !req.auth.mode;
  return !(scriptEmpty && headersEmpty && testsEmpty && docsEmpty && authInherit);
}
// 보정 4: Bruno 의 recursive 러너는 중첩 서브폴더를 부모 폴더의 직속 요청보다 먼저 실행한다.
// 그래서 8.Admin 의 "Admin Login"(직속 요청)이 Coupons/Catalog-Meta 서브폴더 요청들보다 늦게 돌아
// adminAccessToken 이 빈 채로 401 이 난다(newman 은 배열 순서라 미발생). 폴더 pre-request 는 중첩
// 서브폴더 요청 직전에도 실행되므로, 여기서 토큰이 없으면 1회만 로그인해 둔다(guard 로 rate-limit 회피).
const ADMIN_FOLDER_PREREQ = [
  "if (!bru.getEnvVar('adminAccessToken')) {",
  '  await bru.sendRequest({',
  "    method: 'POST',",
  "    url: bru.getEnvVar('baseUrl') + '/auth/login',",
  "    headers: { 'Content-Type': 'application/json' },",
  "    data: { email: bru.getEnvVar('adminEmail'), password: bru.getEnvVar('adminPassword') },",
  '  }, function (err, res) {',
  '    if (!err && res && res.data && res.data.accessToken) {',
  "      bru.setEnvVar('adminAccessToken', res.data.accessToken);",
  '    }',
  '  });',
  '}',
].join('\n');

function folderBru(folderItem) {
  const root = folderItem.root || {};
  const script = (root.request && root.request.script) || {};
  if (/^8\. Admin/.test(folderItem.name)) script.req = ADMIN_FOLDER_PREREQ;
  const j = {
    meta: { name: folderItem.name, seq: folderItem.seq },
    ...(root.request ? { headers: root.request.headers || [], script, vars: root.request.vars || {}, auth: root.request.auth || {} } : { script }),
    docs: root.docs || '',
  };
  return jsonToCollectionBru(j);
}

function writeFolder(dir, items) {
  fs.mkdirSync(dir, { recursive: true });
  for (const it of items) {
    if (it.type === 'folder') {
      const sub = path.join(dir, safe(it.name));
      fs.mkdirSync(sub, { recursive: true });
      fs.writeFileSync(path.join(sub, 'folder.bru'), folderBru(it));
      const tag = tagForFolder(it.name);
      writeFolderChildren(sub, it.items || [], tag);
    } else {
      fs.writeFileSync(path.join(dir, safe(it.name) + '.bru'), reqToBru(it, null));
    }
  }
}
function writeFolderChildren(dir, items, tag) {
  for (const it of items) {
    if (it.type === 'folder') {
      const sub = path.join(dir, safe(it.name));
      fs.mkdirSync(sub, { recursive: true });
      fs.writeFileSync(path.join(sub, 'folder.bru'), folderBru(it));
      writeFolderChildren(sub, it.items || [], tag); // 중첩 폴더는 부모 태그 상속
    } else {
      fs.writeFileSync(path.join(dir, safe(it.name) + '.bru'), reqToBru(it, tag));
    }
  }
}

const pm = JSON.parse(fs.readFileSync(COLLECTION, 'utf8'));
const env = JSON.parse(fs.readFileSync(ENVIRONMENT, 'utf8'));

const bru = await postmanToBruno(pm);
const benv = await postmanToBrunoEnvironment(env);

// 보정 1: secret 플래그 해제 → envJsonToBruV2 가 값을 평문 vars 로 기록한다.
// (Postman 환경파일도 값이 평문이라 비밀 노출 수준은 동일. headless 실행엔 값이 반드시 필요.)
for (const v of benv.variables || []) v.secret = false;

fs.rmSync(OUT, { recursive: true, force: true });
fs.mkdirSync(OUT, { recursive: true });

fs.writeFileSync(path.join(OUT, 'bruno.json'), JSON.stringify({
  version: '1',
  name: bru.name || 'Groove API',
  type: 'collection',
  ignore: ['node_modules', '.git'],
}, null, 2) + '\n');

if (rootHasContent(bru.root)) {
  const cj = {
    ...(bru.root.request ? { headers: bru.root.request.headers || [], script: bru.root.request.script || {}, vars: bru.root.request.vars || {}, auth: bru.root.request.auth || {} } : {}),
    docs: bru.root.docs || '',
  };
  fs.writeFileSync(path.join(OUT, 'collection.bru'), jsonToCollectionBru(cj));
  console.log('• collection.bru 기록(컬렉션 레벨 메타/문서 보존)');
}

writeFolder(OUT, bru.items || []);

const envDir = path.join(OUT, 'environments');
fs.mkdirSync(envDir, { recursive: true });
fs.writeFileSync(path.join(envDir, safe(benv.name || 'Groove Local') + '.bru'), envJsonToBruV2(benv));

let folders = 0, reqs = 0;
function count(items) { for (const it of items) { if (it.type === 'folder') { folders++; count(it.items || []); } else reqs++; } }
count(bru.items || []);
console.log(`✅ 변환 완료: 폴더 ${folders}, 요청 ${reqs}, 환경 1 → bruno/`);
