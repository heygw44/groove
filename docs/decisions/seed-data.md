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

Groove(LP 전문 이커머스 백엔드)는 W8 에 5~10만 건 규모의 시드 데이터를 채워 넣어야 한다. ERD 기준으로 필요한 엔터티와 목표 건수는 다음과 같다.

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

두 부류는 요구가 다르다. 카탈로그(album·artist·genre·label)는 현실성 있는 LP 메타데이터가 있어야 데모가 설득력을 갖는다. 반면 트랜잭션(member·orders·review 등)은 도메인 규칙만 지키는 합성 데이터로도 충분하다.

---

## Decision

두 부류를 각각에 맞는 방법으로 채우는 하이브리드 방식을 택한다.

- **카탈로그 레이어 (album, artist, genre, label)**: Discogs Data Dump 파싱
- **트랜잭션 레이어 (member, orders, cart, payment, shipping, review)**: Python + Faker 스크립트 자체 생성

---

## Considered Options

### Option A — MusicBrainz

PostgreSQL 덤프나 Web API 로 받는 음악 메타데이터 DB 다. 음악 전반을 폭넓게 다루지만 LP 만 쓰려면 `medium.format = 'Vinyl'` 로 걸러야 하고, 포트폴리오 도메인(바이닐)과의 직관적 연결은 약하다. 덤프 스키마가 복잡한 데다 크기가 수십 GB 라 전체 파싱 부담이 크고, 한글 메타데이터는 거의 없다.

| 항목 | 내용 |
|---|---|
| 수급 방법 | PostgreSQL 덤프 다운로드 또는 Web API |
| 라이선스 | 핵심 메타데이터 CC0, 일부 보조 데이터 CC-BY-NC-SA |
| LP 적합도 | 중간 — `format='Vinyl'` 필터 가능하나 도메인 직관성 낮음 |
| 단점 | 덤프 스키마 복잡, 수십 GB, 한글 메타데이터 거의 없음 |

### Option B — Discogs Data Dump ✅ (채택)

Discogs 는 바이닐 중심 DB 라 LP 도메인에 가장 잘 맞는다. 월별 XML 덤프(artists·releases·labels·masters)를 공개하고, 라이선스가 **CC0 1.0 Universal** 이어서 출처 표기 없이 상업·학술·포트폴리오 어디에나 쓸 수 있다. `format = Vinyl` 로 거르면 수백만 건의 releases 에서 목표인 2 만 건을 어렵지 않게 확보한다. 한국 아티스트도 수록돼 있지만 라틴 표기 위주라, 한글 보강은 Faker 쪽에서 처리하면 된다. 다만 덤프가 수 GB 라 파싱 스크립트가 필요한데, 이건 W8-3 범위로 잡는다.

| 항목 | 내용 |
|---|---|
| 수급 방법 | 월별 XML 덤프 (artists.xml, releases.xml, labels.xml, masters.xml) |
| 라이선스 | **CC0 1.0 Universal** — 출처 표기 불요 |
| LP 적합도 | **최고** — 바이닐 중심 DB, `format=Vinyl` 필터 |
| 단점 | XML 덤프 수 GB → 파싱 스크립트 필요 (W8-3 범위) |

### Option C — Python + Faker 단독

전부 Faker 로 생성하는 방식이다. 구현은 가장 쉽지만 실존 아티스트·앨범에 매핑할 수 없어 카탈로그의 현실성이 떨어지고 데모 설득력이 부족하다. 그래서 트랜잭션 레이어에 한해서만 쓴다.

### Option D — 하이브리드 ✅ (최종 채택)

결국 Discogs 의 카탈로그 현실성과 Faker 의 트랜잭션 유연성을 합치는 게 답이었다. 각자 잘하는 영역만 맡긴다.

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

> **구현 결과 (W8-3 / #140)** — 측정용 시드는 카탈로그까지 **Python + Faker 합성**으로 구현했다
> (`db/seed/generate_seed.py`, `scripts/seed.sh`). 본 ADR이 채택한 Discogs Dump 파싱은 수 GB 다운로드·
> 외부 의존을 수반해 `./scripts/seed.sh`의 오프라인·수초 내 재현성과 상충했고, W9 측정의 목표(검색 슬로우
> 쿼리 풀 스캔 재현)는 합성 데이터의 분포(가격·연도·장르·제목)만으로 충분했다. **Discogs 실데이터 주입은
> 후속 과제로 남긴다**(포트폴리오 현실감 보강 시). 적재는 FK-safe TRUNCATE + 멀티로우 INSERT
> (`foreign_key_checks=0`/`autocommit=0` 래핑)이며, 트랜잭션(orders/payment/review) 대량 시드는 아직 미구현.

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
- CC0 라이선스 → GitHub 공개 저장소에 데이터 포함 가능 (attribution 불필요)
- 카탈로그 현실성과 트랜잭션 유연성을 동시에 달성

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
