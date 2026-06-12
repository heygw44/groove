-- V13: 리뷰 — review (ERD §4.14, glossary §2.10).
--
-- W7-7 (#59) 범위. 배송 완료(DELIVERED 이상)된 본인 회원 주문의 주문 항목(album)에 대해 1~5점 리뷰를
-- 작성/조회/삭제한다. 게스트 주문은 리뷰 불가 — member_id NOT NULL 이 그 경계다 (주문 본인 검증은 APP).
--
-- [W5] (UNIQUE + 내 리뷰 목록 인덱스만):
--   - uk_review_order_album UNIQUE (order_id, album_id)  -- 1주문-1상품-1리뷰
--   - idx_review_member     (member_id)                  -- 내 리뷰 목록용
--
-- [W10] (슬로우 쿼리 측정 후 추가, 본 V13 에서는 의도적 누락 — ERD §5.2):
--   - idx_review_album_created (album_id, created_at)    -- 상품별 리뷰 조회
--     ※ album_id 단일 조회는 fk_review_album 인덱스로 받쳐지므로 W5 기능 자체는 동작한다.
--
-- 비즈니스 룰 위치:
--   - 1주문-1상품-1리뷰                                  : DB UNIQUE (uk_review_order_album) + APP(existsByOrderIdAndAlbumId 선검증)
--   - rating 1~5                                          : DB CHECK (ck_review_rating_range, MySQL 8) + APP(@Min/@Max + Review.write 재검증)
--   - 본인 주문에만 리뷰 허용                              : APP (ReviewService — order.memberId == 인증 memberId)
--   - 배송 완료(DELIVERED 이상) 주문에만 리뷰 허용         : APP (ReviewService — order.status ∈ {DELIVERED, COMPLETED})
--   - 해당 주문에 album 이 포함돼야 함                     : APP (ReviewService — order.items 중 album 존재)
--   - 본인 리뷰만 삭제                                    : APP (ReviewService — review.member.id == 인증 memberId)
--   - FK 참조 무결성:
--       review.member_id → member  ON DELETE CASCADE  (회원 삭제 시 리뷰 동반 삭제)
--       review.album_id  → album   ON DELETE CASCADE  (앨범 삭제 시 리뷰 동반 삭제)
--       review.order_id  → orders  ON DELETE CASCADE  (payment / shipping / order_item 과 동일)
CREATE TABLE review (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    member_id  BIGINT       NOT NULL,
    album_id   BIGINT       NOT NULL,
    order_id   BIGINT       NOT NULL,
    rating     TINYINT      NOT NULL,
    content    TEXT         NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_review_rating_range CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT uk_review_order_album UNIQUE (order_id, album_id),
    CONSTRAINT fk_review_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_album FOREIGN KEY (album_id) REFERENCES album (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_order FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    INDEX idx_review_member (member_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
