// 한정반 부하/카오스 테스트가 공유하는 setup 헬퍼(관리자 로그인, 상품/드롭 생성, 회원 준비, 오픈 재스케줄).
// __ENV 를 직접 읽지 않는다 — limited-purchase.js 와 limited-chaos.js 가 서로 다른 기본값을 쓰기 때문에
// 설정값은 전부 호출부에서 인자로 받는다.

import http from 'k6/http';
import { fail } from 'k6';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

export function pad(value) {
  return value < 10 ? `0${value}` : `${value}`;
}

// date(절대 시각) 를 offsetMinutes 로 표현되는 타임존의 벽시계 문자열(LocalDateTime, 초 단위)로 바꾼다.
// k6 실행 머신의 로컬 타임존과 무관하게 서버가 이해하는 시각 문자열을 만들기 위해 UTC 기준으로 계산한다.
export function toLocalDateTimeString(date, offsetMinutes) {
  const shifted = new Date(date.getTime() + offsetMinutes * 60000);
  const year = shifted.getUTCFullYear();
  const month = pad(shifted.getUTCMonth() + 1);
  const day = pad(shifted.getUTCDate());
  const hour = pad(shifted.getUTCHours());
  const minute = pad(shifted.getUTCMinutes());
  const second = pad(shifted.getUTCSeconds());
  return `${year}-${month}-${day}T${hour}:${minute}:${second}`;
}

// "...+09:00" / "...-05:30" 꼬리에서 오프셋(분)을 뽑아낸다.
export function parseOffsetMinutes(offsetDateTime) {
  const matched = offsetDateTime.match(/([+-])(\d{2}):(\d{2})$/);
  if (!matched) {
    return 0;
  }
  const sign = matched[1] === '-' ? -1 : 1;
  return sign * (Number(matched[2]) * 60 + Number(matched[3]));
}

export function chunk(array, size) {
  const chunks = [];
  for (let i = 0; i < array.length; i += size) {
    chunks.push(array.slice(i, i + size));
  }
  return chunks;
}

export function authHeader(token) {
  return { Authorization: `Bearer ${token}`, ...JSON_HEADERS };
}

export function timestamp() {
  const now = new Date();
  // 같은 분에 두 번 실행하면 앞 결과가 덮어써지므로 초까지 붙인다.
  return `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`;
}

export function adminLogin(baseUrl, adminEmail, adminPassword) {
  const res = http.post(`${baseUrl}/api/v1/auth/login`, JSON.stringify({
    email: adminEmail,
    password: adminPassword,
  }), { headers: JSON_HEADERS, tags: { name: 'setup_admin_login' } });

  if (res.status !== 200) {
    fail(`관리자 로그인에 실패했습니다. local 프로파일 시드 계정을 확인하세요. status=${res.status}`);
  }
  return res.json('data.accessToken');
}

export function firstArtistId(baseUrl) {
  const res = http.get(`${baseUrl}/api/v1/artists`, { tags: { name: 'setup_artists' } });
  const artists = res.json('data') || [];
  if (artists.length === 0) {
    fail('아티스트 목록이 비어 있습니다. local 프로파일 시드가 필요합니다.');
  }
  return artists[0].id;
}

export function createProduct(baseUrl, adminToken, artistId, stock, titlePrefix) {
  const res = http.post(`${baseUrl}/api/v1/admin/products`, JSON.stringify({
    title: `${titlePrefix}${Date.now()}`,
    artistId,
    price: 45000,
    initialStock: stock,
    // 상품은 앨범에 속해야 한다. 부하 테스트용 상품은 매번 새 앨범으로 만든다.
    newAlbum: { title: `${titlePrefix}ALBUM-${Date.now()}` },
  }), { headers: authHeader(adminToken), tags: { name: 'setup_product_create' } });

  if (res.status !== 201) {
    fail(`상품 생성에 실패했습니다. status=${res.status} body=${res.body}`);
  }
  return res.json('data.id');
}

export function createDrop(baseUrl, adminToken, productId, stock) {
  const now = new Date();
  const openAt = toLocalDateTimeString(new Date(now.getTime() + 60 * 60000), 540);
  const closeAt = toLocalDateTimeString(new Date(now.getTime() + 120 * 60000), 540);

  const res = http.post(`${baseUrl}/api/v1/admin/limited-drops`, JSON.stringify({
    productId,
    totalQuantity: stock,
    perMemberLimit: 1,
    openAt,
    closeAt,
  }), { headers: authHeader(adminToken), tags: { name: 'setup_drop_create' } });

  if (res.status !== 201) {
    fail(`한정반 드롭 생성에 실패했습니다. status=${res.status} body=${res.body}`);
  }
  return res.json('data.id');
}

export function getDropDetail(baseUrl, dropId) {
  const res = http.get(`${baseUrl}/api/v1/limited-drops/${dropId}`, { tags: { name: 'setup_drop_detail' } });
  if (res.status !== 200) {
    fail(`한정반 상세 조회에 실패했습니다. status=${res.status} body=${res.body}`);
  }
  return res.json('data');
}

// openAt 을 서버 시각(serverTime) 기준 openDelaySec 뒤로 다시 잡고, 오픈을 강제한다.
export function rescheduleAndForceOpen(baseUrl, adminToken, dropId, openDelaySec) {
  const detail = getDropDetail(baseUrl, dropId);
  const offsetMinutes = parseOffsetMinutes(detail.serverTime);
  const serverNow = new Date(detail.serverTime);
  const openAtDate = new Date(serverNow.getTime() + openDelaySec * 1000);
  const closeAtDate = new Date(openAtDate.getTime() + 60 * 60000);

  const updateRes = http.patch(`${baseUrl}/api/v1/admin/limited-drops/${dropId}`, JSON.stringify({
    openAt: toLocalDateTimeString(openAtDate, offsetMinutes),
    closeAt: toLocalDateTimeString(closeAtDate, offsetMinutes),
  }), { headers: authHeader(adminToken), tags: { name: 'setup_drop_update' } });

  if (updateRes.status !== 200) {
    fail(`한정반 드롭 오픈 시각 수정에 실패했습니다. status=${updateRes.status} body=${updateRes.body}`);
  }

  // 강제 오픈은 status/Redis 재고만 바꾸고 openAt 은 그대로 두므로, 실제 구매 가능은 openAt 도달 이후다.
  const openRes = http.patch(`${baseUrl}/api/v1/admin/limited-drops/${dropId}/open`, null,
      { headers: authHeader(adminToken), tags: { name: 'setup_drop_open' } });
  if (openRes.status !== 200) {
    fail(`한정반 드롭 강제 오픈에 실패했습니다. status=${openRes.status} body=${openRes.body}`);
  }

  return openAtDate;
}

function signupMember(baseUrl, memberEmailPrefix, memberPassword, memberNicknamePrefix, index) {
  return ['POST', `${baseUrl}/api/v1/auth/signup`, JSON.stringify({
    email: `${memberEmailPrefix}${index}@groove.com`,
    password: memberPassword,
    nickname: `${memberNicknamePrefix}${index}`,
  }), { headers: JSON_HEADERS, tags: { name: 'setup_signup' } }];
}

function loginMember(baseUrl, memberEmailPrefix, memberPassword, index) {
  return ['POST', `${baseUrl}/api/v1/auth/login`, JSON.stringify({
    email: `${memberEmailPrefix}${index}@groove.com`,
    password: memberPassword,
  }), { headers: JSON_HEADERS, tags: { name: 'setup_login' } }];
}

function getAddresses(baseUrl, token) {
  return ['GET', `${baseUrl}/api/v1/members/me/addresses`, null,
    { headers: authHeader(token), tags: { name: 'setup_address_get' } }];
}

function createAddress(baseUrl, token) {
  return ['POST', `${baseUrl}/api/v1/members/me/addresses`, JSON.stringify({
    recipientName: '로드테스트',
    phone: '010-1234-5678',
    zipCode: '12345',
    address1: '부하테스트로 1',
    isDefault: true,
  }), { headers: authHeader(token), tags: { name: 'setup_address_create' } }];
}

// memberCount 명 만큼 회원을 만들고 로그인·배송지까지 준비한다. batchSize 씩 나눠 처리한다.
// options: { baseUrl, memberCount, memberEmailPrefix, memberNicknamePrefix, memberPassword, batchSize }
export function createMembers(options) {
  const {
    baseUrl, memberCount, memberEmailPrefix, memberNicknamePrefix, memberPassword, batchSize,
  } = options;

  const indexes = [];
  for (let i = 1; i <= memberCount; i += 1) {
    indexes.push(i);
  }

  const users = [];
  chunk(indexes, batchSize).forEach((batch) => {
    http.batch(batch.map((index) => signupMember(baseUrl, memberEmailPrefix, memberPassword, memberNicknamePrefix, index)));

    const loginResponses = http.batch(batch.map((index) => loginMember(baseUrl, memberEmailPrefix, memberPassword, index)));
    const tokens = loginResponses.map((res, i) => {
      if (res.status !== 200) {
        fail(`회원 로그인에 실패했습니다. index=${batch[i]} status=${res.status} body=${res.body}`);
      }
      return res.json('data.accessToken');
    });

    const addressResponses = http.batch(tokens.map((token) => getAddresses(baseUrl, token)));
    const needsAddress = [];
    const addressIds = tokens.map((token, i) => {
      const addresses = addressResponses[i].json('data') || [];
      if (addresses.length > 0) {
        return addresses[0].id;
      }
      needsAddress.push(i);
      return null;
    });

    if (needsAddress.length > 0) {
      const createdResponses = http.batch(needsAddress.map((i) => createAddress(baseUrl, tokens[i])));
      needsAddress.forEach((i, position) => {
        const res = createdResponses[position];
        if (res.status !== 201) {
          fail(`배송지 생성에 실패했습니다. index=${batch[i]} status=${res.status} body=${res.body}`);
        }
        addressIds[i] = res.json('data.id');
      });
    }

    tokens.forEach((token, i) => {
      users.push({ token, addressId: addressIds[i] });
    });

    if (batch[0] % 200 === 1 || batch[0] === 1) {
      console.log(`회원 준비 진행: ${users.length}/${memberCount}`);
    }
  });

  return users;
}
