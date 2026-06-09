#!/usr/bin/env python3
"""측정용 대규모 시드 데이터 생성기 (이슈 #140).

Faker 합성으로 카탈로그(genre/label/artist/album)와 테스트 회원을 만들어
멀티로우 INSERT SQL(seed.sql)을 출력한다. W9 측정(검색 슬로우 쿼리·flash-sale)용이며,
데모 시더 ``LocalDataSeeder``(@Profile("local"))와는 완전히 별개 경로다.

핵심 결정
  - 재현성   : SEED 고정 (Faker.seed + random.seed) → 동일 입력 → 동일 seed.sql
  - 적재     : FK-safe TRUNCATE 후 멀티로우 INSERT (Context7 MySQL 가이드 — foreign_key_checks/
               unique_checks/autocommit 래핑으로 대량 적재 최적화)
  - id       : TRUNCATE 가 AUTO_INCREMENT 를 1 로 리셋하므로 명시 id(1..N)로 FK 를 결정적 연결
  - 계정     : ProductionSeedGuard 충돌을 피하려 데모 이메일(@groove.dev)과 분리된
               ``loadtest*@groove.test`` 네임스페이스 사용

규모/동작은 환경 변수로 오버라이드 (기본값은 이슈 #140 기준):
  SEED=42 GENRE_COUNT=12 LABEL_COUNT=80 ARTIST_COUNT=2000 ALBUM_COUNT=50000
  MEMBER_COUNT=80 LIMITED_COUNT=40 SINGLE_STOCK_COUNT=8
  OUT=db/seed/seed.sql SEED_PASSWORD=Test1234!
"""
import hashlib
import os
import random
import sys

import bcrypt
from faker import Faker

# ── 설정 (env 오버라이드) ──────────────────────────────────────────────
SEED = int(os.getenv("SEED", "42"))
GENRE_COUNT = int(os.getenv("GENRE_COUNT", "12"))
LABEL_COUNT = int(os.getenv("LABEL_COUNT", "80"))
ARTIST_COUNT = int(os.getenv("ARTIST_COUNT", "2000"))
ALBUM_COUNT = int(os.getenv("ALBUM_COUNT", "50000"))
MEMBER_COUNT = int(os.getenv("MEMBER_COUNT", "80"))      # USER 수 (ADMIN 1 별도)
LIMITED_COUNT = int(os.getenv("LIMITED_COUNT", "40"))    # 한정반 30~50
SINGLE_STOCK_COUNT = int(os.getenv("SINGLE_STOCK_COUNT", "8"))  # 단일재고 5~10
DEFAULT_PASSWORD = "Test1234!"
# 기본 비밀번호의 사전 계산 BCrypt 해시 — bcrypt.gensalt()는 os.urandom 기반이라 매 실행 달라져
# seed.sql 결정성을 깨므로, 기본 경로는 고정 해시를 재사용한다(Spring BCryptPasswordEncoder 호환, strength 10).
DEFAULT_PW_HASH = "$2b$10$.vchwuio/eGU/KaxjHjjgu0t7ru91T19DtzsDqkpZiUWsLP3Lfjee"
SEED_PASSWORD = os.getenv("SEED_PASSWORD", DEFAULT_PASSWORD)
OUT = os.getenv("OUT", os.path.join(os.path.dirname(os.path.abspath(__file__)), "seed.sql"))

BATCH = 1000                      # 멀티로우 INSERT 배치 크기
NOW = "2026-01-01 00:00:00"       # 결정적 타임스탬프 (created_at/updated_at, DATETIME(6))

# ENUM — DB 스키마(AlbumFormat/AlbumStatus/MemberRole)와 정확히 일치해야 함
FORMATS = ["LP_12", "LP_DOUBLE", "EP", "SINGLE_7"]
FORMAT_WEIGHTS = [70, 12, 12, 6]
STATUS_SELLING, STATUS_SOLD_OUT, STATUS_HIDDEN = "SELLING", "SOLD_OUT", "HIDDEN"
ROLE_USER, ROLE_ADMIN = "USER", "ADMIN"

GENRE_NAMES = [
    "Rock", "Jazz", "Pop", "Electronic", "Hip-Hop", "Classical",
    "Soul", "Funk", "Reggae", "Blues", "Folk", "Metal",
    "Country", "R&B", "Ambient",
]
LABEL_SUFFIXES = ["Records", "Recordings", "Music", "Sound", "Tapes", "Vinyl", "Audio"]
# 검색 슬로우 쿼리(LIKE '%kw%') 결과 크기가 키워드별로 달라지도록 흔한 토큰을 일부 제목에 주입
COMMON_TOKENS = ["Love", "Night", "Blue", "Dream", "Fire", "Heart", "City", "Sun", "Rain", "Gold"]

# FK-safe TRUNCATE 순서 (자식 → 부모). foreign_key_checks=0 안전망과 병행.
TRUNCATE_TABLES = [
    "member_coupon", "coupon", "review", "shipping", "payment",
    "order_item", "orders", "cart_item", "cart", "idempotency_record",
    "refresh_token", "album", "artist", "label", "genre", "member",
]

fake = Faker("en_US")            # 카탈로그(앨범 제목·아티스트명) — 음반 현실감
fake_ko = Faker("ko_KR")         # 회원 이름


def _esc(s: str) -> str:
    """SQL 문자열 리터럴 이스케이프 (백슬래시·작은따옴표)."""
    return s.replace("\\", "\\\\").replace("'", "''")


def q(s: str) -> str:
    return "'" + _esc(s) + "'"


def email_hash(email: str) -> str:
    """member.email_hash (V18 #186) — 재가입 차단용 점유 해시. NOT NULL + UNIQUE(uk_member_email_hash).
    마이그레이션 백필 공식 SHA2(LOWER(TRIM(email)),256) 과 동일한 legacy(SHA-256) 해시를 채운다.
    로그인 조회에는 쓰이지 않으므로(평문 email 로 조회) 서버 HMAC 비밀키 없이 결정적으로 생성 가능."""
    return hashlib.sha256(email.strip().lower().encode()).hexdigest()


def write_rows(f, table: str, columns: str, rows: list) -> None:
    """미리 렌더링된 '(...)' 행 문자열들을 멀티로우 INSERT 로 배치 출력."""
    for i in range(0, len(rows), BATCH):
        chunk = rows[i:i + BATCH]
        f.write(f"INSERT INTO {table} {columns} VALUES\n")
        f.write(",\n".join(chunk))
        f.write(";\n")


def main() -> None:
    Faker.seed(SEED)
    fake.seed_instance(SEED)
    fake_ko.seed_instance(SEED)
    random.seed(SEED)

    genre_count = min(GENRE_COUNT, len(GENRE_NAMES))
    # 기본 비밀번호는 고정 해시(결정성)·커스텀이면 즉석 해시(살트 랜덤 — 결정성 비보장은 의도)
    if SEED_PASSWORD == DEFAULT_PASSWORD:
        pw_hash = DEFAULT_PW_HASH
    else:
        pw_hash = bcrypt.hashpw(SEED_PASSWORD.encode(), bcrypt.gensalt(rounds=10)).decode()

    with open(OUT, "w", encoding="utf-8") as f:
        f.write("-- 이 파일은 db/seed/generate_seed.py 가 생성한다. 직접 수정 금지 (이슈 #140).\n")
        f.write(f"-- SEED={SEED} ALBUM_COUNT={ALBUM_COUNT} MEMBER_COUNT={MEMBER_COUNT}\n")
        f.write("SET foreign_key_checks=0;\nSET unique_checks=0;\nSET autocommit=0;\n\n")
        for t in TRUNCATE_TABLES:
            f.write(f"TRUNCATE TABLE {t};\n")
        f.write("\n")

        # ── genre ──────────────────────────────────────────────────────
        rows = [
            f"({i + 1},{q(GENRE_NAMES[i])},{q(NOW)},{q(NOW)})"
            for i in range(genre_count)
        ]
        write_rows(f, "genre", "(id,name,created_at,updated_at)", rows)

        # ── label (name UNIQUE) ────────────────────────────────────────
        seen, label_rows = set(), []
        while len(label_rows) < LABEL_COUNT:
            name = f"{fake.last_name()} {random.choice(LABEL_SUFFIXES)}"
            if name in seen or len(name) > 100:
                continue
            seen.add(name)
            label_rows.append(
                f"({len(label_rows) + 1},{q(name)},{q(NOW)},{q(NOW)})"
            )
        write_rows(f, "label", "(id,name,created_at,updated_at)", label_rows)

        # ── artist (name NOT unique, description nullable) ──────────────
        artist_rows = []
        for i in range(ARTIST_COUNT):
            desc = q(fake.sentence(nb_words=10)[:1900]) if random.random() < 0.3 else "NULL"
            artist_rows.append(
                f"({i + 1},{q(fake.name()[:200])},{desc},{q(NOW)},{q(NOW)})"
            )
        write_rows(f, "artist", "(id,name,description,created_at,updated_at)", artist_rows)

        # ── album (스트리밍 배치 — 대량) ────────────────────────────────
        limited_ids = set(random.sample(range(1, ALBUM_COUNT + 1), min(LIMITED_COUNT, ALBUM_COUNT)))
        remaining = [i for i in range(1, ALBUM_COUNT + 1) if i not in limited_ids]
        single_ids = set(random.sample(remaining, min(SINGLE_STOCK_COUNT, len(remaining))))

        cols = ("(id,title,artist_id,genre_id,label_id,release_year,format,price,"
                "stock,status,is_limited,cover_image_url,description,created_at,updated_at)")
        batch = []
        for album_id in range(1, ALBUM_COUNT + 1):
            words = [fake.word().capitalize() for _ in range(random.randint(1, 3))]
            if random.random() < 0.15:
                words.insert(random.randint(0, len(words)), random.choice(COMMON_TOKENS))
            title = " ".join(words)[:300] or "Untitled"

            artist_id = random.randint(1, ARTIST_COUNT)
            genre_id = random.randint(1, genre_count)
            label_id = "NULL" if random.random() < 0.1 else str(random.randint(1, LABEL_COUNT))
            year = int(random.triangular(1960, 2025, 2015))
            fmt = random.choices(FORMATS, weights=FORMAT_WEIGHTS, k=1)[0]
            price = random.randrange(15000, 150001, 1000)

            is_limited = album_id in limited_ids
            if album_id in single_ids:
                stock = 1
            elif is_limited:
                stock = random.randint(30, 100)
            elif random.random() < 0.05:
                stock = 0
            else:
                stock = random.randint(10, 200)

            if stock == 0:
                status = STATUS_SOLD_OUT
            elif random.random() < 0.03:
                status = STATUS_HIDDEN
            else:
                status = STATUS_SELLING

            batch.append(
                f"({album_id},{q(title)},{artist_id},{genre_id},{label_id},{year},"
                f"{q(fmt)},{price},{stock},{q(status)},{1 if is_limited else 0},"
                f"NULL,NULL,{q(NOW)},{q(NOW)})"
            )
            if len(batch) >= BATCH:
                write_rows(f, "album", cols, batch)
                batch.clear()
        if batch:
            write_rows(f, "album", cols, batch)

        # ── member (테스트 회원 + ADMIN 1) — loadtest*@groove.test ──────
        member_cols = ("(id,email,email_hash,password,name,phone,role,email_verified,"
                       "deleted_at,created_at,updated_at)")
        member_rows = []
        for i in range(1, MEMBER_COUNT + 1):
            email = f"loadtest{i:03d}@groove.test"
            phone = f"010-{random.randint(1000, 9999)}-{random.randint(1000, 9999)}"
            member_rows.append(
                f"({i},{q(email)},{q(email_hash(email))},{q(pw_hash)},"
                f"{q(fake_ko.name()[:50])},{q(phone)},{q(ROLE_USER)},1,NULL,{q(NOW)},{q(NOW)})"
            )
        admin_id = MEMBER_COUNT + 1
        admin_email = "loadtest-admin@groove.test"
        admin_phone = f"010-{random.randint(1000, 9999)}-{random.randint(1000, 9999)}"
        member_rows.append(
            f"({admin_id},{q(admin_email)},{q(email_hash(admin_email))},{q(pw_hash)},"
            f"{q('관리자')},{q(admin_phone)},{q(ROLE_ADMIN)},1,NULL,{q(NOW)},{q(NOW)})"
        )
        write_rows(f, "member", member_cols, member_rows)

        f.write("\nCOMMIT;\nSET unique_checks=1;\nSET foreign_key_checks=1;\n")

    print(
        f"[generate_seed] OUT={OUT}\n"
        f"  genre={genre_count} label={LABEL_COUNT} artist={ARTIST_COUNT} "
        f"album={ALBUM_COUNT} (limited={len(limited_ids)} single_stock={len(single_ids)})\n"
        f"  member={MEMBER_COUNT} USER + 1 ADMIN  | 공유 비밀번호='{SEED_PASSWORD}' (BCrypt {pw_hash[:7]}...)",
        file=sys.stderr,
    )


if __name__ == "__main__":
    main()
