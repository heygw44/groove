# API 설계: Groove (LP 전문 이커머스 백엔드)

| 항목 | 값 |
|---|---|
| 버전 | v1 |
| 작성일 | 2026-05-05 |
| 베이스 URL | `/api/v1` |
| 응답 포맷 | `application/json` (정상), `application/problem+json` (에러) |
| 시간 | ISO 8601 UTC (예: `2026-05-05T14:23:00.000Z`) |
| 통화 | 원 단위 정수 (KRW) |
| 관련 문서 | PRD.md, ARCHITECTURE.md, ERD.md |

---

## 1. 공통 규칙

### 1.1 인증
- 인증 필요 시: `Authorization: Bearer {accessToken}` 헤더
- Access Token TTL: 30분
- Refresh Token TTL: 14일

### 1.2 공통 헤더

**요청**
| 헤더 | 필수 | 설명 |
|---|---|---|
| `Authorization` | 인증 API에서 필수 | `Bearer {token}` |
| `Content-Type` | 본문 있을 시 | `application/json` |
| `X-Request-Id` | 선택 | 미설정 시 서버에서 생성하여 응답에 포함 |
| `Idempotency-Key` | 결제 요청 등에서 필수 | UUID 권장, 24시간 유지 |

**응답**
| 헤더 | 설명 |
|---|---|
| `X-Request-Id` | 요청 추적 ID |

### 1.3 페이지네이션

**요청 (쿼리 파라미터)**
| 이름 | 기본값 | 설명 |
|---|---|---|
| `page` | 0 | 0-based |
| `size` | 20 | 최대 100 |
| `sort` | (도메인별 디폴트) | 형식: `field,asc` 또는 `field,desc`. 다중 정렬 가능 |

**응답 포맷 (PageResponse)**
```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 1234,
  "totalPages": 62,
  "first": true,
  "last": false
}
```

### 1.4 에러 응답 (RFC 7807 ProblemDetail)

```json
{
  "type": "https://groove.example/errors/insufficient-stock",
  "title": "재고가 부족합니다",
  "status": 409,
  "detail": "Album ID 123의 재고가 부족합니다. 요청 5, 가능 2",
  "instance": "/api/v1/orders",
  "code": "ORDER_INSUFFICIENT_STOCK",
  "timestamp": "2026-05-05T14:23:00.000Z",
  "traceId": "9f1c..."
}
```

**공통 에러 코드 패턴**
| HTTP | 사용처 |
|---|---|
| 400 | 입력 검증 실패, 잘못된 요청 |
| 401 | 인증 실패 (토큰 없음·만료·위조) |
| 403 | 권한 부족 |
| 404 | 리소스 없음 |
| 409 | 충돌 (중복, 상태 전이 위반, 재고 부족) |
| 422 | 비즈니스 룰 위반 |
| 429 | Rate Limit 초과 |
| 500 | 서버 내부 오류 |

**도메인별 에러 코드 (예시)**
| code | 의미 |
|---|---|
| `AUTH_INVALID_CREDENTIALS` | 이메일/비밀번호 불일치 |
| `AUTH_TOKEN_EXPIRED` | 토큰 만료 |
| `AUTH_TOKEN_REUSED` | 토큰 재사용 감지 (탈취 의심) |
| `MEMBER_EMAIL_DUPLICATED` | 이메일 중복 |
| `ORDER_INSUFFICIENT_STOCK` | 재고 부족 |
| `ORDER_INVALID_STATE_TRANSITION` | 주문 상태 전이 불가 |
| `PAYMENT_IDEMPOTENCY_CONFLICT` | 멱등성 키 중복 처리 중 |
| `PAYMENT_GATEWAY_FAILURE` | PG 응답 실패 |

### 1.5 멱등성

- 결제 요청 `POST /payments` 등 일부 엔드포인트는 `Idempotency-Key` 헤더 필수
- 동일 키 + 처리 완료 → 기존 응답 그대로 반환 (200)
- 동일 키 + 처리 중 → `409 PAYMENT_IDEMPOTENCY_CONFLICT`
- 키 미지정 → `400 IDEMPOTENCY_KEY_REQUIRED`

### 1.6 Rate Limit

- 초과 시 `429 Too Many Requests` + `Retry-After` 헤더
- 적용 대상 (예시):
  - `POST /auth/login`: IP당 분당 10회
  - `POST /auth/signup`: IP당 분당 3회
  - `POST /payments`: 회원당 분당 5회

---

## 2. 엔드포인트 일람

### 인증 (`/auth`)
| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| POST | `/auth/signup` | Public | 회원가입 |
| POST | `/auth/login` | Public | 로그인 |
| POST | `/auth/refresh` | Public (Refresh Token) | 토큰 갱신 |
| POST | `/auth/logout` | Authenticated | 로그아웃 |

### 회원 (`/members`)
| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/members/me` | USER | 내 정보 조회 |
| PATCH | `/members/me` | USER | 내 정보 수정 |
| DELETE | `/members/me` | USER | 회원 탈퇴 (soft delete) |
| GET | `/members/me/orders` | USER | 내 주문 목록 |

### 카탈로그 (`/albums`, `/artists`, `/genres`, `/labels`)
| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/albums` | Public | 상품 목록 (검색/필터/정렬) |
| GET | `/albums/{id}` | Public | 상품 상세 |
| GET | `/artists` | Public | 아티스트 목록 |
| GET | `/artists/{id}` | Public | 아티스트 상세 |
| GET | `/artists/{id}/albums` | Public | 아티스트의 앨범 목록 |
| GET | `/genres` | Public | 장르 목록 |
| GET | `/labels` | Public | 레이블 목록 |

### 장바구니 (`/cart`)
| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/cart` | USER | 장바구니 조회 |
| POST | `/cart/items` | USER | 항목 추가 |
| PATCH | `/cart/items/{itemId}` | USER | 수량 변경 |
| DELETE | `/cart/items/{itemId}` | USER | 항목 삭제 |
| DELETE | `/cart` | USER | 비우기 |

### 주문 (`/orders`)
| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| POST | `/orders` | Public | 주문 생성 (회원 + 게스트) |
| GET | `/orders/{orderNumber}` | USER | 주문 조회 (회원 본인) |
| POST | `/orders/{orderNumber}/cancel` | USER | 주문 취소 |
| POST | `/orders/{orderNumber}/guest-lookup` | Public | 게스트 주문 조회 |

### 결제 (`/payments`)
| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| POST | `/payments` | Public | 결제 요청 (멱등성 키 필수) |
| GET | `/payments/{paymentId}` | Public | 결제 조회 |
| POST | `/payments/webhook` | Public (서명 검증) | PG 웹훅 콜백 |

### 배송 (`/shippings`)
| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/shippings/{trackingNumber}` | Public | 배송 조회 (운송장 번호) |

### 리뷰 (`/reviews`, `/albums/{id}/reviews`)
| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| POST | `/reviews` | USER | 리뷰 작성 |
| GET | `/albums/{id}/reviews` | Public | 상품 리뷰 목록 |
| DELETE | `/reviews/{reviewId}` | USER (본인) | 리뷰 삭제 |

### 관리자 (`/admin`)
| 메서드 | 경로 | 권한 | 설명 |
|---|---|---|---|
| GET | `/admin/orders` | ADMIN | 전체 주문 조회 |
| PATCH | `/admin/orders/{orderNumber}/status` | ADMIN | 주문 상태 강제 변경 |
| POST | `/admin/orders/{orderNumber}/refund` | ADMIN | 환불 처리 |
| POST | `/admin/albums` | ADMIN | 상품 등록 |
| PUT | `/admin/albums/{id}` | ADMIN | 상품 수정 |
| DELETE | `/admin/albums/{id}` | ADMIN | 상품 삭제 |
| PATCH | `/admin/albums/{id}/stock` | ADMIN | 재고 조정 |
| (CRUD) | `/admin/artists`, `/admin/genres`, `/admin/labels` | ADMIN | 카탈로그 메타 CRUD |

---

## 3. 엔드포인트 상세

### 3.1 인증

#### POST `/auth/signup` — 회원가입

**Request**
```json
{
  "email": "user@example.com",
  "password": "P@ssw0rd!2024",
  "name": "김철수",
  "phone": "01012345678"
}
```

**검증**
- email: 형식, 중복 불가
- password: 최소 10자, 영문·숫자·특수문자 조합
- name: 1~50자
- phone: 선택, 숫자만 10~11자

**Response 201**
```json
{
  "memberId": 1,
  "email": "user@example.com",
  "name": "김철수",
  "createdAt": "2026-05-05T14:23:00.000Z"
}
```

**Errors**
- 400 `VALIDATION_FAILED`
- 409 `MEMBER_EMAIL_DUPLICATED`
- 429 Rate Limit

---

#### POST `/auth/login` — 로그인

**Request**
```json
{
  "email": "user@example.com",
  "password": "P@ssw0rd!2024"
}
```

**Response 200**
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",
  "tokenType": "Bearer",
  "expiresIn": 1800
}
```

**Errors**
- 401 `AUTH_INVALID_CREDENTIALS`
- 429 Rate Limit

---

#### POST `/auth/refresh` — 토큰 갱신

**Request**
```json
{
  "refreshToken": "eyJhbGc..."
}
```

**Response 200**
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "eyJhbGc...",  // Rotation: 새 토큰 발급
  "tokenType": "Bearer",
  "expiresIn": 1800
}
```

**Errors**
- 401 `AUTH_TOKEN_EXPIRED`
- 401 `AUTH_TOKEN_REUSED` — 이미 사용된 토큰 재사용 감지 시 → 해당 사용자의 모든 토큰 무효화

---

#### POST `/auth/logout`

**Headers**: `Authorization: Bearer ...`

**Request**
```json
{
  "refreshToken": "eyJhbGc..."
}
```

**Response 204** No Content

---

### 3.2 회원

#### GET `/members/me`

**Headers**: `Authorization: Bearer ...`

**Response 200**
```json
{
  "memberId": 1,
  "email": "user@example.com",
  "name": "김철수",
  "phone": "01012345678",
  "role": "USER",
  "emailVerified": true,
  "createdAt": "2026-05-05T14:23:00.000Z"
}
```

---

#### PATCH `/members/me`

**Request** (변경 필드만)
```json
{
  "name": "김영희",
  "phone": "01098765432"
}
```

**Response 200** (`/members/me` 동일 스키마)

---

#### DELETE `/members/me` — 회원 탈퇴 (soft delete)

**Response 204**

---

#### GET `/members/me/orders` — 내 주문 목록

**Query**: `page=0&size=20&sort=createdAt,desc&status=DELIVERED` (status 선택)

**Response 200**: PageResponse<OrderSummary>
```json
{
  "content": [
    {
      "orderNumber": "ORD-20260505-XXXX",
      "status": "DELIVERED",
      "totalAmount": 45000,
      "itemCount": 2,
      "representativeAlbumTitle": "Abbey Road",
      "createdAt": "2026-05-05T14:23:00.000Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 12,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

---

### 3.3 카탈로그

#### GET `/albums` — 상품 목록 / 검색

**Query 파라미터**
| 이름 | 타입 | 설명 |
|---|---|---|
| `keyword` | string | 앨범명 + 아티스트명 OR 매칭 |
| `genreId` | long | 장르 필터 |
| `artistId` | long | 아티스트 필터 |
| `minPrice` | long | 최저가 |
| `maxPrice` | long | 최고가 |
| `minYear` | int | 발매연도 시작 |
| `maxYear` | int | 발매연도 끝 |
| `format` | string | LP_12 / LP_DOUBLE / EP / SINGLE_7 |
| `isLimited` | boolean | 한정반만 |
| `status` | string | 기본 SELLING (Public은 SELLING만 노출) |
| `page`, `size`, `sort` | | 공통 페이징 |

**정렬 키**: `createdAt`, `price`, `releaseYear`, (집계) `salesCount`

**Response 200**: PageResponse<AlbumSummary>
```json
{
  "content": [
    {
      "id": 1,
      "title": "Abbey Road",
      "artist": { "id": 10, "name": "The Beatles" },
      "genre": { "id": 2, "name": "Rock" },
      "label": { "id": 5, "name": "Apple Records" },
      "releaseYear": 1969,
      "format": "LP_12",
      "price": 35000,
      "stock": 8,
      "status": "SELLING",
      "isLimited": false,
      "coverImageUrl": "https://...",
      "averageRating": 4.8,
      "reviewCount": 120
    }
  ],
  "page": 0, "size": 20, "totalElements": 1234,
  "totalPages": 62, "first": true, "last": false
}
```

---

#### GET `/albums/{id}` — 상품 상세

**Response 200**
```json
{
  "id": 1,
  "title": "Abbey Road",
  "artist": { "id": 10, "name": "The Beatles", "description": "..." },
  "genre": { "id": 2, "name": "Rock" },
  "label": { "id": 5, "name": "Apple Records" },
  "releaseYear": 1969,
  "format": "LP_12",
  "price": 35000,
  "stock": 8,
  "status": "SELLING",
  "isLimited": false,
  "coverImageUrl": "https://...",
  "description": "Mastered from the original tapes...",
  "averageRating": 4.8,
  "reviewCount": 120,
  "createdAt": "2026-01-15T00:00:00.000Z"
}
```

**Errors**
- 404 `ALBUM_NOT_FOUND`

---

#### GET `/artists` / `/artists/{id}` / `/artists/{id}/albums`

생략 — 카탈로그 패턴과 동일 구조.

#### GET `/genres`, `/labels`

단순 목록 조회. 페이징 없이 전체 반환 (소량 데이터).

---

### 3.4 장바구니

#### GET `/cart`

**Headers**: `Authorization: Bearer ...`

**Response 200**
```json
{
  "cartId": 1,
  "items": [
    {
      "itemId": 11,
      "albumId": 1,
      "albumTitle": "Abbey Road",
      "artistName": "The Beatles",
      "coverImageUrl": "https://...",
      "unitPrice": 35000,
      "quantity": 2,
      "subtotal": 70000,
      "available": true
    }
  ],
  "totalAmount": 70000,
  "totalItemCount": 2
}
```

`available`: 상품 상태가 SELLING이고 재고 충분한지

---

#### POST `/cart/items`

**Request**
```json
{
  "albumId": 1,
  "quantity": 2
}
```

이미 담겨있으면 수량 누적, 처음이면 새로 추가.

**Response 201** (장바구니 전체 반환)

**Errors**
- 404 `ALBUM_NOT_FOUND`
- 422 `ALBUM_NOT_PURCHASABLE` (HIDDEN, SOLD_OUT 등)
- 422 `CART_QUANTITY_LIMIT_EXCEEDED`

---

#### PATCH `/cart/items/{itemId}` / DELETE `/cart/items/{itemId}` / DELETE `/cart`

생략 — 표준 패턴.

---

### 3.5 주문 ★

#### POST `/orders` — 주문 생성

회원/게스트 모두 사용. `Authorization` 헤더 유무로 구분.

**회원 주문 Request**
```json
{
  "items": [
    { "albumId": 1, "quantity": 2 },
    { "albumId": 7, "quantity": 1 }
  ],
  "shipping": {
    "recipientName": "김철수",
    "recipientPhone": "01012345678",
    "address": "서울시 강남구 테헤란로 123",
    "addressDetail": "456호",
    "zipCode": "06234",
    "safePackagingRequested": true
  }
}
```

**게스트 주문 Request** (헤더 없음)
```json
{
  "guest": {
    "email": "guest@example.com",
    "phone": "01098765432"
  },
  "items": [...],
  "shipping": {...}
}
```

**처리**
1. 입력 검증 + 게스트/회원 분기
2. 트랜잭션 시작
3. 각 album 재고 검증 + 차감 (v1: 비관적 락)
4. Order, OrderItem 저장 (가격·앨범명 스냅샷)
5. 트랜잭션 종료
6. orderNumber 발급 후 반환

**Response 201**
```json
{
  "orderNumber": "ORD-20260505-A1B2C3",
  "status": "PENDING",
  "totalAmount": 105000,
  "items": [
    {
      "albumId": 1,
      "albumTitle": "Abbey Road",
      "unitPrice": 35000,
      "quantity": 2,
      "subtotal": 70000
    }
  ],
  "shipping": {
    "recipientName": "...", ...
  },
  "createdAt": "2026-05-05T14:23:00.000Z"
}
```

**Errors**
- 400 `VALIDATION_FAILED`
- 404 `ALBUM_NOT_FOUND`
- 409 `ORDER_INSUFFICIENT_STOCK`
- 422 `ALBUM_NOT_PURCHASABLE`

---

#### GET `/orders/{orderNumber}` — 주문 조회 (회원)

**Headers**: `Authorization: Bearer ...`

본인 주문이 아니면 404 (정보 노출 방지).

**Response 200** (주문 생성 응답과 동일 + payment, shipping 상태 포함)

---

#### POST `/orders/{orderNumber}/cancel`

**Request** (선택 사유)
```json
{
  "reason": "단순 변심"
}
```

**조건**: 상태가 PENDING 또는 PAID(환불 정책에 따라)

**처리**: 상태 → CANCELLED, 재고 복원, 결제 완료 시 환불 처리

**Response 200**: 주문 객체

**Errors**
- 409 `ORDER_INVALID_STATE_TRANSITION`

---

#### POST `/orders/{orderNumber}/guest-lookup` — 게스트 주문 조회

**Request**
```json
{
  "email": "guest@example.com"
}
```

매칭 실패 시 404 (반복 시도 시 Rate Limit 적용).

**Response 200**: 주문 객체

---

### 3.6 결제 ★

#### POST `/payments` — 결제 요청

**Headers**
- `Idempotency-Key: {uuid}` 필수
- `Authorization` (회원) 또는 게스트 정보 (요청 본문)

**Request**
```json
{
  "orderNumber": "ORD-20260505-A1B2C3",
  "method": "CARD"
}
```

**처리**
1. Idempotency-Key 조회 → 처리 완료면 기존 응답 반환
2. 처리 중이면 409
3. 신규면 PaymentGateway.request() 호출 (Mock)
4. Payment(PENDING) 저장
5. 응답 반환 — **이 시점에 결제 완료 아님**. 웹훅 콜백 후 확정.

**Response 202** (PENDING 상태 결제 접수)
```json
{
  "paymentId": 1,
  "orderNumber": "ORD-20260505-A1B2C3",
  "amount": 105000,
  "status": "PENDING",
  "method": "CARD",
  "pgProvider": "MOCK",
  "createdAt": "2026-05-05T14:23:00.000Z"
}
```

**Errors**
- 400 `IDEMPOTENCY_KEY_REQUIRED`
- 404 `ORDER_NOT_FOUND`
- 409 `PAYMENT_IDEMPOTENCY_CONFLICT`
- 409 `ORDER_INVALID_STATE_TRANSITION` (PENDING이 아닌 주문)
- 502 `PAYMENT_GATEWAY_FAILURE`

---

#### GET `/payments/{paymentId}` — 결제 상태 조회

폴링용. 클라이언트가 PENDING → PAID 전환을 확인할 때 사용.

**Response 200**: Payment 객체

---

#### POST `/payments/webhook` — PG 웹훅 콜백

**Headers**: `X-Mock-Signature` (모킹용 서명, 실 PG에서는 PG별 서명 헤더)

**Request** (Mock PG가 자동 호출)
```json
{
  "pgTransactionId": "mock-tx-abc123",
  "orderNumber": "ORD-20260505-A1B2C3",
  "status": "PAID",
  "paidAt": "2026-05-05T14:23:05.000Z"
}
```

**처리**
1. 서명 검증
2. Payment 상태 갱신 (PENDING → PAID 또는 FAILED)
3. Order 상태 갱신 (PENDING → PAID 또는 PAYMENT_FAILED)
4. (PAID 시) `OrderPaidEvent` 발행 → 배송 엔트리 생성
5. (FAILED 시) 재고 복원

**Response 200** `{"received": true}`

**중복 수신**
- 같은 (pgTransactionId, status) 두 번째 호출은 idempotent하게 무시
- 실 PG 환경에서는 PG가 200 받을 때까지 재시도하므로 멱등 보장 필수

---

### 3.7 배송

#### GET `/shippings/{trackingNumber}`

**Response 200**
```json
{
  "trackingNumber": "8a4f-...",
  "status": "SHIPPED",
  "recipientName": "김철수",
  "address": "서울시 강남구 ...",
  "safePackagingRequested": true,
  "shippedAt": "2026-05-06T10:00:00.000Z",
  "deliveredAt": null,
  "createdAt": "2026-05-05T14:25:00.000Z"
}
```

---

### 3.8 리뷰

#### POST `/reviews`

**Headers**: `Authorization: Bearer ...`

**Request**
```json
{
  "orderNumber": "ORD-20260505-A1B2C3",
  "albumId": 1,
  "rating": 5,
  "content": "음질 정말 좋네요"
}
```

**조건**
- 본인 주문
- 주문 상태 DELIVERED 이상
- 해당 주문에 해당 albumId 존재
- 동일 (order, album) 리뷰 미작성

**Response 201**: Review 객체

**Errors**
- 403 `REVIEW_NOT_OWNED`
- 422 `REVIEW_ORDER_NOT_DELIVERED`
- 409 `REVIEW_DUPLICATED`

---

#### GET `/albums/{id}/reviews`

**Query**: `page`, `size`, `sort=createdAt,desc`

**Response 200**: PageResponse<Review>
```json
{
  "content": [
    {
      "reviewId": 1,
      "memberName": "김**",
      "rating": 5,
      "content": "음질 정말 좋네요",
      "createdAt": "2026-05-15T10:00:00.000Z"
    }
  ],
  ...
}
```

회원명은 마스킹 처리.

---

#### DELETE `/reviews/{reviewId}`

본인만 가능. **204 No Content**.

---

### 3.9 관리자 (`/admin`)

권한: `ADMIN`. 모든 엔드포인트는 `Authorization` + 권한 체크.

#### GET `/admin/orders`

**Query**: `page`, `size`, `status`, `memberEmail`, `from`, `to`

**Response 200**: PageResponse<OrderSummary>

#### PATCH `/admin/orders/{orderNumber}/status`

**Request**
```json
{
  "newStatus": "PREPARING",
  "reason": "수동 진행"
}
```

상태 머신 검증 통과 시 적용.

**Errors**: 409 `ORDER_INVALID_STATE_TRANSITION`

#### POST `/admin/orders/{orderNumber}/refund`

**Request**: `{ "reason": "..." }`

처리: Payment REFUNDED + Order CANCELLED + (필요 시) 재고 복원.

#### POST/PUT/DELETE `/admin/albums`, `/admin/albums/{id}`

표준 CRUD. 등록 시 artist_id, genre_id, label_id 유효성 검증.

#### PATCH `/admin/albums/{id}/stock`

**Request**: `{ "delta": 10 }` (음수도 가능, 최종 재고 음수 불가)

**Response 200**: 갱신된 Album 객체

#### `/admin/artists`, `/admin/genres`, `/admin/labels`

표준 CRUD. 동일 패턴이므로 상세 생략.

---

### 3.10 헬스체크

#### GET `/actuator/health`

Spring Boot Actuator 기본. 외부 노출 시 보안 검토 필요 (v1은 단순 노출).

**Response 200** `{"status": "UP"}`

---

## 4. 응답 객체 표준 (요약)

| 객체 | 주요 필드 |
|---|---|
| `MemberResponse` | memberId, email, name, phone, role, emailVerified, createdAt |
| `AlbumSummary` | id, title, artist, genre, label, releaseYear, format, price, stock, status, isLimited, coverImageUrl, averageRating, reviewCount |
| `AlbumDetail` | AlbumSummary + description, createdAt |
| `CartResponse` | cartId, items[], totalAmount, totalItemCount |
| `CartItem` | itemId, albumId, albumTitle, artistName, coverImageUrl, unitPrice, quantity, subtotal, available |
| `OrderResponse` | orderNumber, status, totalAmount, items[], shipping, payment, createdAt |
| `OrderSummary` | orderNumber, status, totalAmount, itemCount, representativeAlbumTitle, createdAt |
| `PaymentResponse` | paymentId, orderNumber, amount, status, method, pgProvider, paidAt, createdAt |
| `ShippingResponse` | trackingNumber, status, recipientName, address, safePackagingRequested, shippedAt, deliveredAt, createdAt |
| `Review` | reviewId, memberName(마스킹), rating, content, createdAt |
| `PageResponse<T>` | content, page, size, totalElements, totalPages, first, last |

---

## 5. 시연 시나리오 — Postman 컬렉션 구성

W8에 완성될 Postman 컬렉션의 주요 폴더:

```
Groove API
├── 0. Health
├── 1. Auth
│   ├── Sign Up
│   ├── Login (응답에서 토큰 자동 환경변수 저장)
│   ├── Refresh Token
│   └── Logout
├── 2. Catalog (Public)
│   ├── List Albums (with filters)
│   ├── Get Album Detail
│   └── List Genres
├── 3. Member Flow (E2E) ★
│   ├── 1) Add to Cart
│   ├── 2) Get Cart
│   ├── 3) Create Order
│   ├── 4) Request Payment (Idempotency-Key 자동 생성)
│   ├── 5) Get Payment Status (polling)
│   ├── 6) Get Order Detail
│   ├── 7) Get Shipping Status
│   └── 8) Write Review (after DELIVERED)
├── 4. Guest Flow
│   ├── Create Order (Guest)
│   ├── Lookup Order (Guest)
│   └── Request Payment
├── 5. Admin
│   ├── Create Album
│   ├── Update Album
│   ├── Change Order Status
│   └── Refund Order
└── 6. Edge Cases (시연용)
    ├── Refresh Token 재사용 → 401
    ├── Idempotency 중복 키 → 200 (기존 응답)
    ├── 재고 부족 주문 → 409
    └── 배송 완료 전 리뷰 → 422
```

---

## 6. OpenAPI / Swagger

- SpringDoc OpenAPI 사용 (`org.springdoc:springdoc-openapi-starter-webmvc-ui`)
- 엔드포인트: `/swagger-ui/index.html`, OpenAPI 문서 `/v3/api-docs`
- 인증 스키마: Bearer JWT 등록
- `@Operation`, `@ApiResponses`로 각 컨트롤러에 명세 작성
- 이 마크다운 문서는 사람이 읽는 설계서, Swagger는 코드와 동기화되는 살아있는 명세

---

## 7. 결정 사항 / 미해결

### 결정됨
- 페이지: 0-based 오프셋. 커서 기반은 v2 후보 (검색 페이지가 깊어질 때 검토)
- 응답 시간: 항상 UTC ISO 8601. 클라이언트에서 변환
- 토큰 발급 위치: 응답 본문 (HttpOnly 쿠키는 v2에서 검토 — CSRF 정책 함께)

### 결정 완료 (W2)
- **비밀번호 재설정 흐름**: v1 미포함. 이메일 발송 인프라가 없어 시연 가치 낮음. v2 후보.
- **이메일 인증 토큰 검증**: v1 미포함. 회원가입 직후 인증 없이 바로 사용 가능. v2 후보.
- **관리자 통계/대시보드 API**: v1 미포함, v2 후보.
