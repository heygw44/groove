-- V7: 장바구니 — cart, cart_item (ERD §4.7, §4.8).
--
-- W6-1 (#41) 범위. 회원당 장바구니 1개 (uk_cart_member) 와 cart_item UNIQUE
-- (uk_cart_item_cart_album) 으로 동일 상품 중복 행 자체를 DB 에서 차단한다.
-- 동일 상품 재추가는 애플리케이션이 기존 행의 quantity 를 누적하는 방식으로 흡수한다 —
-- DB UNIQUE 는 동시성 시 최종 방어선으로 동작한다 (CartService 에서 1회 재시도).
--
-- [W6] (UNIQUE + FK 기본 인덱스만):
--   - uk_cart_member             UNIQUE (member_id)
--   - uk_cart_item_cart_album    UNIQUE (cart_id, album_id)
--   - idx_cart_item_album        (album_id)  -- FK 기본
--
-- [W10] 추가 후보 없음 (ERD §4.7, §4.8). 인덱스 시연 대상은 album 쪽이다.
--
-- 비즈니스 룰 위치:
--   - quantity > 0           : DB CHECK + 도메인 메서드(@Min(1)) 이중 방어선
--   - 회원당 장바구니 1개      : DB UNIQUE
--   - 동일 상품 중복 추가 불가  : DB UNIQUE (앱은 기존 행 누적으로 흡수)
--   - 비활성 상품 추가 거부    : APP (CartService — SELLING 만 허용)
--   - FK 참조 무결성          : DB ON DELETE — cart 삭제 시 cart_item CASCADE,
--                                              album 삭제 시 RESTRICT (장바구니가 album 을 잡고 있을 수 있음)
CREATE TABLE cart (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    member_id  BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_cart_member UNIQUE (member_id),
    CONSTRAINT fk_cart_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE cart_item (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    cart_id    BIGINT      NOT NULL,
    album_id   BIGINT      NOT NULL,
    quantity   INT         NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_cart_item_quantity_positive CHECK (quantity > 0),
    CONSTRAINT uk_cart_item_cart_album UNIQUE (cart_id, album_id),
    CONSTRAINT fk_cart_item_cart  FOREIGN KEY (cart_id)  REFERENCES cart  (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_item_album FOREIGN KEY (album_id) REFERENCES album (id) ON DELETE RESTRICT,
    INDEX idx_cart_item_album (album_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
