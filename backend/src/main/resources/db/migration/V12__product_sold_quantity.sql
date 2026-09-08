-- 상품 목록 인기순 정렬이 매 요청 order_item 42만 행을 GROUP BY 로 집계해 1.5~2.5초가 걸린다.
-- 판매 수량을 product 행에 비정규화해두고 판매량이 바뀌는 시점에만 재계산하는 방식으로 바꾼다.
-- 컬럼을 추가하면 default 0 이 판매 이력 없는 상품을 이미 덮으므로, 백필은 이력 있는 상품만 갱신하면 된다.

alter table product add column sold_quantity bigint not null default 0;

update product p
join (select oi.product_id, sum(oi.quantity) as q
      from order_item oi join orders o on o.id = oi.order_id
      where o.status in ('PAID', 'PREPARING', 'SHIPPED', 'DELIVERED')
      group by oi.product_id) s on s.product_id = p.id
set p.sold_quantity = s.q;

-- 인기순 정렬 키(sold_quantity, review_count, created_at, id)가 전부 DESC 라 방향이 엇갈리지 않아
-- 역순 인덱스 스캔 하나로 정렬이 해소된다. 5만 행을 읽고 filesort 하던 것이 40행 스캔이 된다.
create index idx_product_sold_review_created on product (sold_quantity, review_count, created_at, id);
