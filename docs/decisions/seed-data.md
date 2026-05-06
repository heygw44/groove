# ADR: 시드 데이터 출처 및 수급 방법 결정

| 항목 | 값 |
|---|---|
| 상태 | Accepted |
| 날짜 | 2026-05-05 |
| 연관 이슈 | #3 |
| 작성자 | ParkGunWoo |
| 후속 작업 | #W8-3 시드 데이터 스크립트 작성 |

---

## Context

Groove(LP 전문 이커머스 백엔드)는 W8에 **5~10만 건 규모의 시드 데이터**를 투입해야 한다.

필요한 엔터티(ERD 기준):

| 엔터티 | 목표 건수 | 성격 |
|---|---|---|
| `artist` | ~2,000 | 카탈로그 |
| `genre` | ~20 | 카탈로그 |
| `label` | ~1,000 | 카탈로그 |
| `album` | ~20,000 | 카탈로그 (LP 한정) |
| `member` | ~1,000 | 트랜잭션 |
| `cart` / `cart_item` | 적당 | 트랜잭션 |
| `orders` / `order_item` | ~30,000 | 트랜잭션 |
| `payment` / `shipping` | 주문 수와 1:1 | 트랜잭션 |
| `review` | ~10,000 | 트랜잭션 |

카탈로그 데이터(album, artist, genre, label)는 **현실성이 있는 LP 메타데이터**가 필요하다.
트랜잭션 데이터(member, orders, review 등)는 도메인 규칙을 따르는 **합성 데이터**면 충분하다.

---

## Decision

**하이브리드 방식**: Discogs 공개 데이터 덤프 + Python(Faker) 자체 생성 스크립트 조합을 채택한다.

- **카탈로그 레이어 (album, artist, genre, label)**: Discogs Data Dump 파싱
- **트랜잭션 레이어 (member, orders, cart, payment, shipping, review)**: Python + Faker 스크립트 자체 생성

---

## Considered Options

### Option A — MusicBrainz

| 항목 | 내용 |
|---|---|
| 수급 방법 | PostgreSQL 덤프 다운로드 또는 Web API |
| 라이선스 | 핵심 메타데이터 CC0, 일부 보조 데이터 CC-BY-NC-SA |
| 규모 | 음악 전반 (LP 한정 필터 필요) |
| LP 적합도 | 중간 — `medium.format = 'Vinyl'` 필터 가능하나 포트폴리오 도메인과 직관성 낮음 |
| 덤프 크기 | 수십 GB (전체 파싱 오버헤드 큼) |
| 단점 | 덤프 스키마 복잡, 한글 메타데이터 거의 없음 |

### Option B — Discogs Data Dump ✅ (채택)

| 항목 | 내용 |
|---|---|
| 수급 방법 | 월별 XML 덤프 공개 (artists.xml, releases.xml, labels.xml, masters.xml) |
| 라이선스 | **CC0 1.0 Universal** — 출처 표기 불요, 상업적·학술적·포트폴리오 모두 허용 |
| URL | https://discogs-data-dumps.s3-us-west-2.amazonaws.com/index.html |
| LP 적합도 | **최고** — Discogs는 바이닐 중심 DB, `format = Vinyl` 필터로 LP만 추출 |
| 규모 | releases 수백만 건 → 필터 후 20,000건 확보 용이 |
| 한글 지원 | 한국 아티스트 수록되나 라틴 표기 위주. 보강은 Faker로 처리 |
| 단점 | XML 덤프 파일 수 GB → 파싱 스크립트 필요 (W8-3 범위) |

### Option C — Python + Faker 단독

| 항목 | 내용 |
|---|---|
| 라이선스 | 해당 없음 |
| 구현 난이도 | 낮음 |
| 현실성 | **낮음** — 실존 아티스트/앨범 매핑 불가, 포트폴리오 데모 설득력 부족 |
| 결론 | 트랜잭션 레이어 한정으로 사용 |

### Option D — 하이브리드 ✅ (최종 채택)

Discogs(카탈로그 현실성) + Faker(트랜잭션 유연성)의 장점을 결합.

---

## 비교 요약

| 기준 | MusicBrainz | Discogs Dump | Faker 단독 | **하이브리드** |
|---|---|---|---|---|
| LP 도메인 적합도 | 중 | **고** | 낮음 | **고** |
| 라이선스 부담 | CC0/CC-BY 혼재 | **CC0** | 없음 | **CC0** |
| 현실성 | 고 | **고** | 낮음 | **고** |
| 구현 난이도 | 고 (스키마 복잡) | 중 | **저** | 중 |
| 한글 지원 | 낮음 | 낮음 | **자유** | 중 (Faker 보강) |
| 5~10만 건 달성 | 가능 | **용이** | **용이** | **용이** |

---

## Implementation Notes (W8-3 인풋)

### Discogs 덤프 파싱 전략

1. **다운로드**: `discogs_YYYYMMDD_releases.xml.gz` (최신 월 덤프)
2. **LP 필터**: `<format name="Vinyl">` 태그 포함 releases만 추출
3. **ERD 매핑**:
   - `artists.xml` → `artist` 테이블 (name, country)
   - `labels.xml` → `label` 테이블 (name)
   - `releases.xml` → `album` 테이블 (title, release_year, price 등) + genre/style → `genre`
4. **처리 도구**: Python SAX 파서 (메모리 효율) → MySQL INSERT 배치

### Faker 생성 전략 (트랜잭션)

- `member`: Faker.name(), email(), phone() — 1,000건
- `cart` / `cart_item`: member당 0~1개, 아이템 1~5개
- `orders` / `order_item`: member당 0~30개 주문, 상태 분포(PAID 60%, DELIVERED 30%, CANCELLED 10%)
- `payment`: 주문과 1:1 (amount = order_item 합계)
- `shipping`: DELIVERED 주문과 1:1
- `review`: DELIVERED 주문 중 40% 작성

### 가격 합성

Discogs는 가격 정보 미제공 → `price_per_unit` = Faker.random_int(15000, 150000, step=1000) (원 단위)

---

## Consequences

**긍정적**
- LP 도메인에 최적화된 실데이터로 포트폴리오 설득력 확보
- CC0 라이선스 → GitHub 공개 저장소 데이터 포함 가능 (attribution 불필요)
- 카탈로그 현실성 + 트랜잭션 유연성 동시 달성

**부정적 / 트레이드오프**
- W8-3에 XML 파서 스크립트 구현 비용 추가 (~2~3시간)
- Discogs 덤프 다운로드 용량 수 GB → 로컬 처리 시간 필요
- 한글 메타데이터 부족 → 데모 시 영문 앨범/아티스트명 노출

---

## References

- [Discogs Data Dumps](https://www.discogs.com/developers/#page:database,header:database-image) — CC0 라이선스 공식 확인
- [Discogs S3 Dump Index](https://discogs-data-dumps.s3-us-west-2.amazonaws.com/index.html)
- [MusicBrainz Data License](https://musicbrainz.org/doc/MusicBrainz_Database/Download)
- [Faker (Python)](https://faker.readthedocs.io/en/master/)
- 연관 ERD: [../ERD.md](../ERD.md)
