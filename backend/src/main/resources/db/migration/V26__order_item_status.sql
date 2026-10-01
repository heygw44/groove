-- 상품주문(order_item) 단위 이행 상태를 도입한다. Order.status(결제 생애주기)와 분리해 상품마다
-- 다른 단계를 가질 수 있게 한다. 이번 배포에서는 아직 상품 단위 취소·발송 API가 없어, 기존 주문 단위
-- 전이가 상품주문 상태도 같이 옮기는 호환 shim(Order 엔티티)만으로 두 상태를 항상 일치시킨다.
-- status 는 문서(03-erd.md) 표기 VARCHAR(20) 대신 30을 쓴다. CANCELED_BY_NOPAYMENT 가 21자라 20으로는 들어가지 않는다.
ALTER TABLE order_item
    ADD COLUMN status varchar(30) NOT NULL DEFAULT 'PAYMENT_PENDING',
    ADD COLUMN claim_status varchar(20) NULL,
    ADD COLUMN product_order_number varchar(40) NULL,
    ADD COLUMN discount_share decimal(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN courier_code varchar(20) NULL,
    ADD COLUMN tracking_number varchar(50) NULL,
    ADD COLUMN prepared_at datetime(6) NULL,
    ADD COLUMN shipped_at datetime(6) NULL,
    ADD COLUMN delivered_at datetime(6) NULL,
    ADD COLUMN confirmed_at datetime(6) NULL,
    ADD COLUMN canceled_at datetime(6) NULL;

-- 결제 생애주기(orders.status) → 상품주문 상태. PENDING 은 placed_at 유무로 결제 전/가상계좌 입금대기를 가른다.
UPDATE order_item oi
JOIN orders o ON o.id = oi.order_id
SET oi.status = CASE
        WHEN o.status = 'PENDING' AND o.placed_at IS NULL THEN 'PAYMENT_PENDING'
        WHEN o.status = 'PENDING' AND o.placed_at IS NOT NULL THEN 'PAYMENT_WAITING'
        WHEN o.status = 'PAID' THEN 'PAID'
        WHEN o.status = 'PREPARING' THEN 'PREPARING'
        WHEN o.status = 'SHIPPED' THEN 'SHIPPING'
        WHEN o.status = 'DELIVERED' THEN 'DELIVERED'
        WHEN o.status IN ('CANCELED', 'REFUNDED') THEN 'CANCELED'
        ELSE oi.status
    END;

-- 가상계좌 발급 후(placed_at 있음) 입금 없이(payment.approved_at 없음) 입금기한이 지나 만료된 주문만
-- CANCELED_BY_NOPAYMENT 로 바꾼다. 입금 전 구매자가 직접 취소한 주문은 Order.cancel 과 같이 CANCELED 로 둔다.
UPDATE order_item oi
JOIN orders o ON o.id = oi.order_id
LEFT JOIN payment p ON p.order_id = o.id
SET oi.status = 'CANCELED_BY_NOPAYMENT'
WHERE o.status = 'CANCELED' AND o.cancel_reason = 'EXPIRED' AND o.placed_at IS NOT NULL
  AND p.approved_at IS NULL;

-- 취소·미입금취소로 끝난 상품주문은 주문의 취소 시각을 그대로 옮긴다.
UPDATE order_item oi
JOIN orders o ON o.id = oi.order_id
SET oi.canceled_at = o.canceled_at
WHERE o.status IN ('CANCELED', 'REFUNDED');

-- 상품주문번호 `{주문번호}-{순번 2자리}`. 순번은 같은 주문 안 order_item.id 오름차순이다.
UPDATE order_item oi
JOIN (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY order_id ORDER BY id) AS rn
    FROM order_item
) numbered ON numbered.id = oi.id
JOIN orders o ON o.id = oi.order_id
SET oi.product_order_number = CONCAT(o.order_number, '-', LPAD(numbered.rn, 2, '0'));

ALTER TABLE order_item
    MODIFY COLUMN product_order_number varchar(40) NOT NULL;

ALTER TABLE order_item
    ADD CONSTRAINT uk_order_item_product_order_number UNIQUE (product_order_number);

CREATE INDEX idx_order_item_status ON order_item (status, delivered_at);

-- 쿠폰 할인액을 라인 금액(단가 x 수량) 비율로 나눈다. 원 미만은 버린다(FLOOR). 할인이 없는 주문은 0 그대로 둔다.
UPDATE order_item oi
JOIN orders o ON o.id = oi.order_id
SET oi.discount_share = CASE
        WHEN o.discount_amount = 0 OR o.total_amount = 0 THEN 0
        ELSE FLOOR(o.discount_amount * (oi.price_snapshot * oi.quantity) / o.total_amount)
    END;

-- 배분 후 남는 원 단위(할인액 - 배분 합)는 주문 안에서 라인 금액이 가장 큰 상품(동률이면 id 가 작은 것)에 더한다.
UPDATE order_item oi
JOIN (
    SELECT ranked.id AS item_id, ranked.remainder
    FROM (
        SELECT oi2.id,
            (o.discount_amount - totals.allocated) AS remainder,
            ROW_NUMBER() OVER (
                PARTITION BY oi2.order_id
                ORDER BY (oi2.price_snapshot * oi2.quantity) DESC, oi2.id ASC
            ) AS rn
        FROM order_item oi2
        JOIN orders o ON o.id = oi2.order_id
        JOIN (
            SELECT order_id, SUM(discount_share) AS allocated
            FROM order_item
            GROUP BY order_id
        ) totals ON totals.order_id = oi2.order_id
        WHERE o.discount_amount > 0
    ) ranked
    WHERE ranked.rn = 1 AND ranked.remainder > 0
) target ON target.item_id = oi.id
SET oi.discount_share = oi.discount_share + target.remainder;

-- 불변식 검증. 위반이 있으면 CHECK 제약이 실패해 마이그레이션 자체가 롤백된다.
CREATE TEMPORARY TABLE v26_invariant (
    violations bigint NOT NULL,
    CONSTRAINT chk_v26_invariant CHECK (violations = 0)
);

-- 주문별 할인 배분 합이 주문의 discount_amount 와 다른 건.
INSERT INTO v26_invariant (violations)
SELECT COUNT(*) FROM (
    SELECT o.id
    FROM orders o
    JOIN order_item oi ON oi.order_id = o.id
    GROUP BY o.id, o.discount_amount
    HAVING SUM(oi.discount_share) <> o.discount_amount
) mismatched_share;

-- 결제가 확정된(placed_at 있음) 주문에 아직 PAYMENT_PENDING 인 상품주문이 남아있는 건.
INSERT INTO v26_invariant (violations)
SELECT COUNT(*)
FROM order_item oi
JOIN orders o ON o.id = oi.order_id
WHERE o.placed_at IS NOT NULL AND oi.status = 'PAYMENT_PENDING';

-- product_order_number 가 NULL 이거나 중복인 건.
INSERT INTO v26_invariant (violations)
SELECT COUNT(*) FROM (
    SELECT product_order_number
    FROM order_item
    WHERE product_order_number IS NULL
    GROUP BY product_order_number
    UNION ALL
    SELECT product_order_number
    FROM order_item
    GROUP BY product_order_number
    HAVING COUNT(*) > 1
) invalid_number;

DROP TEMPORARY TABLE v26_invariant;
