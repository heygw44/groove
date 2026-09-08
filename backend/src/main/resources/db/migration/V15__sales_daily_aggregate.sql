-- 관리자 통계의 일별 매출/인기 상품 조회는 매 요청 payment·order_item 전량을 다시 GROUP BY 한다.
-- 이 마이그레이션은 사전 집계 테이블 스키마만 추가한다. 백필/집계 적재는 이 마이그레이션에 넣지 않는다
-- (로컬은 Flyway off + ddl-auto: update 라 백필이 로컬에서 안 돌아 운영과만 어긋난다).
-- 백필 경로는 관리자 수동 재집계 API 하나로 통일한다.

-- 날짜별 집계. 판매가 0인 날도 0 행을 만든다 — "빈 구간"과 "아직 집계되지 않은 구간"을 구분해야 하기 때문이다.
-- PK 를 (sale_date) 자연키로 둔 이유: 조회가 늘 WHERE sale_date BETWEEN ? 범위라 클러스터드 인덱스 range 스캔으로
-- 끝나고 정렬도 공짜다. 대리키를 쓰면 세컨더리 range 뒤에 PK 랜덤 액세스가 행 수만큼 붙는다.
create table sales_daily (
	sale_date date not null,
	order_count bigint not null default 0,
	sales_amount decimal(14, 2) not null default 0,
	cancel_count bigint not null default 0,
	cancel_amount decimal(14, 2) not null default 0,
	aggregated_at datetime(6) not null,
	created_at datetime(6) not null,
	updated_at datetime(6) not null,
	primary key (sale_date)
) engine=InnoDB;

-- 날짜 x 상품별 집계. sales_daily 와 달리 판매가 없는 (날짜, 상품) 조합은 행을 만들지 않는다
-- (상품 수 x 365일로 행이 만드는 즉시 폭발한다). PK 는 (sale_date, product_id) 자연키 — 이유는 위와 동일하다.
create table sales_daily_product (
	sale_date date not null,
	product_id bigint not null,
	sold_quantity bigint not null default 0,
	sales_amount decimal(14, 2) not null default 0,
	order_count bigint not null default 0,
	aggregated_at datetime(6) not null,
	created_at datetime(6) not null,
	updated_at datetime(6) not null,
	primary key (sale_date, product_id),
	constraint fk_sales_daily_product_product foreign key (product_id) references product (id)
) engine=InnoDB;

-- 대사(재집계 vs 원본 재계산) 결과 이력. 대리키를 쓴다 — 조회가 range 가 아니라
-- 최근순 나열이라 자연 PK 로 얻을 이점이 없다.
create table sales_reconcile_log (
	id bigint not null auto_increment,
	sale_date date not null,
	metric varchar(30) not null,
	severity varchar(20) not null,
	expected_value decimal(14, 2) not null,
	actual_value decimal(14, 2) not null,
	repaired tinyint default false not null,
	created_at datetime(6) not null,
	updated_at datetime(6) not null,
	primary key (id)
) engine=InnoDB;

create index idx_sales_reconcile_log_date on sales_reconcile_log (sale_date, created_at);
