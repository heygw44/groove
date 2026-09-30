-- 상품주문·클레임 쓰기의 낙관적 락 컬럼. 락 이전 스냅샷으로 전체 컬럼을 UPDATE 하던 경로가 다른 트랜잭션의 변경을
-- 되돌리지 못하게 한다(1차 방어는 주문 FOR UPDATE 뒤 READ COMMITTED 로 다시 읽기).
ALTER TABLE order_item ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE order_claim ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
