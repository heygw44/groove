-- 결제 승인·가상계좌 발급 전 주문은 목록·상세에서 숨긴다. placed_at 이 그 확정 시점이다.
ALTER TABLE orders
    ADD COLUMN placed_at datetime(6) NULL,
    ADD COLUMN order_source varchar(10) NOT NULL DEFAULT 'CART';

CREATE INDEX idx_orders_member_placed ON orders (member_id, placed_at);

-- 결제 승인 이력이 있거나(approved_at) 결제완료 이후 상태로 넘어간 주문은 승인 시각(없으면 주문 생성 시각)을 확정 시점으로 본다.
UPDATE orders o
LEFT JOIN payment p ON p.order_id = o.id
SET o.placed_at = COALESCE(p.approved_at, o.created_at)
WHERE p.approved_at IS NOT NULL
   OR o.status IN ('PAID', 'PREPARING', 'SHIPPED', 'DELIVERED', 'REFUNDED');

-- 입금 전 가상계좌(승인 이력 없음)는 발급 시각을 확정 시점으로 백필한다.
UPDATE orders o
JOIN payment p ON p.order_id = o.id
SET o.placed_at = p.created_at
WHERE o.placed_at IS NULL AND p.va_bank_code IS NOT NULL;

-- 한정반 구매 주문은 결제 확정 여부와 무관하게 출처만 표시해 둔다.
UPDATE orders o
JOIN limited_purchase lp ON lp.order_id = o.id
SET o.order_source = 'LIMITED';
