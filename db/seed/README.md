# 측정용 시드 데이터 (이슈 #140)

W9 측정(검색 슬로우 쿼리 재현·flash-sale 부하)을 위한 **대규모 합성 데이터셋** 생성기다.
데모 프론트용 시더 `LocalDataSeeder`(`@Profile("local")`, 12장)와는 **완전히 별개 경로**이며,
이 디렉토리는 Python + [Faker](https://faker.readthedocs.io/)로 카탈로그/회원을 합성한다.

> 카탈로그 출처는 ADR #3(`docs/decisions/seed-data.md`)이 제시한 Discogs Dump 대신 **Faker 합성**을
> 채택했다 — 측정 목적엔 충분하고 오프라인·수초 내 재현이 가능하기 때문. Discogs는 후속 과제로 남긴다.

## 사용법

루트에서 한 번에:

```bash
./scripts/seed.sh --yes              # 로컬 MySQL (localhost:3306)
./scripts/seed.sh --docker --yes     # docker-compose 의 mysql 컨테이너로 적재
```

> ⚠️ 전제: 스키마는 Flyway가 이미 만들어 두어야 한다. 앱을 1회 부팅(`./gradlew bootRun` 또는
> `docker compose up -d`)해 V1~V17 마이그레이션을 적용한 뒤 실행할 것. 이 스크립트는 **데이터만** 적재한다.
> 또한 멱등성을 위해 카탈로그/트랜잭션/회원 테이블을 **TRUNCATE 후 재적재**한다(아래 "동작" 참고).

생성기만 단독 실행(디버깅용):

```bash
python3 -m venv .venv && . .venv/bin/activate
pip install -r db/seed/requirements.txt
python db/seed/generate_seed.py        # → db/seed/seed.sql 생성
```

## 규모 (기본값 · 이슈 #140 기준)

| 항목 | 기본 | 비고 |
|---|---|---|
| `genre` | 12 | 고정 장르 리스트 |
| `label` | 80 | name UNIQUE |
| `artist` | 2,000 | |
| `album` | **50,000** | `ALBUM_COUNT=100000`로 상향 가능 |
| └ 한정반(`is_limited=1`) | 40 | `LIMITED_COUNT` |
| └ 단일 재고(`stock=1`) | 8 | `SINGLE_STOCK_COUNT` |
| `member` | 80 USER + 1 ADMIN | `loadtest*@groove.test` |

환경 변수로 오버라이드: `SEED GENRE_COUNT LABEL_COUNT ARTIST_COUNT ALBUM_COUNT MEMBER_COUNT
LIMITED_COUNT SINGLE_STOCK_COUNT SEED_PASSWORD OUT`. 예) `ALBUM_COUNT=100000 ./scripts/seed.sh --yes`.

## 테스트 계정 (k6 다중 사용자용)

`ProductionSeedGuard`가 감지하는 데모 이메일(`@groove.dev`)과 **분리된 네임스페이스**를 쓴다.

| 구분 | 이메일 | 비밀번호 |
|---|---|---|
| 일반 회원 | `loadtest001@groove.test` … `loadtest080@groove.test` | `Test1234!` |
| 관리자 | `loadtest-admin@groove.test` | `Test1234!` |

비밀번호는 전 계정 공유(Spring `BCryptPasswordEncoder` 호환 해시 1개 재사용). `SEED_PASSWORD`로 변경 가능.

## 동작

- **재현성**: `SEED`(기본 42) 고정 → `Faker.seed`/`random.seed` → 동일 입력은 동일 `seed.sql`.
- **적재(Context7 MySQL 가이드)**: `SET foreign_key_checks=0; unique_checks=0; autocommit=0` 래핑 +
  1,000행 멀티로우 INSERT 배치 → `COMMIT`. 수만 건도 수초 내 적재.
- **멱등성**: 적재 전 FK-safe 순서로 비즈니스 테이블을 `TRUNCATE`(자식→부모) 후 재적재. TRUNCATE가
  AUTO_INCREMENT를 1로 리셋하므로 명시 id(1..N)로 FK를 결정적 연결한다. `flyway_schema_history`는
  건드리지 않는다(스키마 보존). 재실행해도 동일 건수.

## 검증

```sql
SELECT COUNT(*) FROM album;                         -- ≈ 50000
SELECT COUNT(*) FROM album WHERE is_limited = 1;    -- 40
SELECT COUNT(*) FROM album WHERE stock = 1;         -- 8
SELECT COUNT(*) FROM member;                        -- 81 (USER 80 + ADMIN 1)
EXPLAIN SELECT * FROM album WHERE title LIKE '%love%';  -- type=ALL (풀 스캔) → 슬로우 쿼리 재현
```
