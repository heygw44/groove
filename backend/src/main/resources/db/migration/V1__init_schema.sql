-- GROOVE 초기 스키마.
-- Hibernate 스키마 생성 결과(jakarta.persistence.schema-generation)를 그대로 옮긴 베이스라인이라
-- 운영 프로파일의 ddl-auto: validate 와 정확히 일치한다. 손으로 고치지 말고 엔티티를 바꾼 뒤 V2 를 추가한다.

-- 테이블
create table address (is_default tinyint default false not null, created_at datetime(6) not null, id bigint not null auto_increment, member_id bigint not null, updated_at datetime(6) not null, zip_code varchar(10) not null, phone varchar(20) not null, recipient_name varchar(30) not null, address1 varchar(200) not null, address2 varchar(200), primary key (id)) engine=InnoDB;
create table admin_audit_log (admin_id bigint not null, created_at datetime(6) not null, id bigint not null auto_increment, target_id bigint not null, updated_at datetime(6) not null, target_type varchar(30) not null check (target_type in ('PRODUCT','ORDER','COUPON','LIMITED_DROP','MEMBER','PAYMENT')), ip_address varchar(45), action varchar(50) not null check (action in ('PRODUCT_CREATE','PRODUCT_UPDATE','PRODUCT_HIDE','PRODUCT_RESTORE','ORDER_STATUS_CHANGE','COUPON_CREATE','COUPON_UPDATE','COUPON_DISABLE','LIMITED_DROP_CREATE','LIMITED_DROP_UPDATE','LIMITED_DROP_OPEN','LIMITED_DROP_CLOSE','MEMBER_STATUS_CHANGE','PAYMENT_CANCEL','STOCK_ADJUST')), detail TEXT, primary key (id)) engine=InnoDB;
create table artist (created_at datetime(6) not null, id bigint not null auto_increment, updated_at datetime(6) not null, name varchar(100) not null, name_en varchar(100), description TEXT, primary key (id)) engine=InnoDB;
create table cart (created_at datetime(6) not null, id bigint not null auto_increment, member_id bigint not null, updated_at datetime(6) not null, primary key (id)) engine=InnoDB;
create table cart_item (quantity integer default 1 not null, cart_id bigint not null, created_at datetime(6) not null, id bigint not null auto_increment, product_id bigint not null, updated_at datetime(6) not null, primary key (id), constraint chk_cart_item_quantity check (quantity > 0)) engine=InnoDB;
create table coupon (discount_value decimal(10,2) not null, issued_count integer default 0 not null, max_discount_amount decimal(10,2), min_order_amount decimal(10,2) default 0 not null, total_quantity integer, created_at datetime(6) not null, expires_at datetime(6) not null, id bigint not null auto_increment, updated_at datetime(6) not null, version bigint default 0 not null, discount_type varchar(20) not null check (discount_type in ('FIXED','RATE')), status varchar(20) default 'ACTIVE' not null check (status in ('ACTIVE','DISABLED')), code varchar(30) not null, name varchar(50) not null, primary key (id)) engine=InnoDB;
create table genre (created_at datetime(6) not null, id bigint not null auto_increment, updated_at datetime(6) not null, name varchar(50) not null, primary key (id)) engine=InnoDB;
create table label (created_at datetime(6) not null, id bigint not null auto_increment, updated_at datetime(6) not null, country varchar(50), name varchar(100) not null, primary key (id)) engine=InnoDB;
create table limited_drop (per_member_limit integer default 1 not null, sold_count integer default 0 not null, total_quantity integer not null, close_at datetime(6) not null, created_at datetime(6) not null, id bigint not null auto_increment, open_at datetime(6) not null, product_id bigint not null, updated_at datetime(6) not null, status varchar(20) default 'SCHEDULED' not null check (status in ('SCHEDULED','OPEN','SOLD_OUT','CLOSED')), primary key (id)) engine=InnoDB;
create table limited_purchase (quantity integer default 1 not null, created_at datetime(6) not null, drop_id bigint not null, id bigint not null auto_increment, member_id bigint not null, order_id bigint, updated_at datetime(6) not null, primary key (id)) engine=InnoDB;
create table member (created_at datetime(6) not null, id bigint not null auto_increment, updated_at datetime(6) not null, role varchar(20) default 'USER' not null check (role in ('USER','ADMIN')), status varchar(20) default 'ACTIVE' not null check (status in ('ACTIVE','SUSPENDED','WITHDRAWN')), nickname varchar(30) not null, email varchar(100) not null, password varchar(100) not null, primary key (id)) engine=InnoDB;
create table member_coupon (used tinyint default false not null, coupon_id bigint not null, created_at datetime(6) not null, id bigint not null auto_increment, issued_at datetime(6) not null, member_id bigint not null, updated_at datetime(6) not null, used_at datetime(6), used_order_id bigint, primary key (id)) engine=InnoDB;
create table order_item (price_snapshot decimal(10,2) not null, quantity integer not null, created_at datetime(6) not null, id bigint not null auto_increment, order_id bigint not null, product_id bigint not null, updated_at datetime(6) not null, product_name_snapshot varchar(200) not null, primary key (id)) engine=InnoDB;
create table orders (discount_amount decimal(10,2) default 0 not null, final_amount decimal(10,2) not null, total_amount decimal(10,2) not null, canceled_at datetime(6), created_at datetime(6) not null, expires_at datetime(6) not null, id bigint not null auto_increment, member_coupon_id bigint, member_id bigint not null, updated_at datetime(6) not null, zip_code varchar(10) not null, phone varchar(20) not null, status varchar(20) default 'PENDING' not null check (status in ('PENDING','PAID','PREPARING','SHIPPED','DELIVERED','CANCELED','REFUNDED')), order_number varchar(30) not null, recipient_name varchar(30) not null, address1 varchar(200) not null, address2 varchar(200), cancel_reason varchar(200), primary key (id)) engine=InnoDB;
create table payment (amount decimal(10,2) not null, approved_at datetime(6), canceled_at datetime(6), created_at datetime(6) not null, id bigint not null auto_increment, order_id bigint not null, updated_at datetime(6) not null, status varchar(20) default 'READY' not null check (status in ('READY','DONE','CANCELED','FAILED')), method varchar(30), toss_order_id varchar(64) not null, payment_key varchar(200), fail_reason varchar(300), primary key (id)) engine=InnoDB;
create table product (avg_rating decimal(2,1), price decimal(10,2) not null, release_date date, review_count integer default 0 not null, artist_id bigint not null, created_at datetime(6) not null, id bigint not null auto_increment, label_id bigint, updated_at datetime(6) not null, status varchar(20) default 'ON_SALE' not null check (status in ('ON_SALE','SOLD_OUT','HIDDEN')), color_variant varchar(50), pressing_info varchar(100), title varchar(200) not null, description TEXT, primary key (id)) engine=InnoDB;
create table product_genre (genre_id bigint not null, id bigint not null auto_increment, product_id bigint not null, primary key (id)) engine=InnoDB;
create table product_image (sort_order integer default 0 not null, created_at datetime(6) not null, id bigint not null auto_increment, product_id bigint not null, updated_at datetime(6) not null, image_url varchar(500) not null, primary key (id)) engine=InnoDB;
create table review (rating integer not null, created_at datetime(6) not null, id bigint not null auto_increment, member_id bigint not null, product_id bigint not null, updated_at datetime(6) not null, title varchar(100), content TEXT, primary key (id)) engine=InnoDB;
create table stock (quantity integer default 0 not null, created_at datetime(6) not null, id bigint not null auto_increment, product_id bigint not null, updated_at datetime(6) not null, version bigint default 0 not null, primary key (id), constraint chk_stock_quantity check (quantity >= 0)) engine=InnoDB;
create table stock_history (quantity_delta integer not null, created_at datetime(6) not null, id bigint not null auto_increment, stock_id bigint not null, updated_at datetime(6) not null, change_type varchar(20) not null check (change_type in ('IN','OUT','CANCEL','ADJUST')), reason varchar(200), primary key (id)) engine=InnoDB;
create table wishlist (created_at datetime(6) not null, id bigint not null auto_increment, member_id bigint not null, product_id bigint not null, updated_at datetime(6) not null, primary key (id)) engine=InnoDB;

-- 인덱스 / 유니크 제약
create index idx_audit_log_admin_created on admin_audit_log (admin_id, created_at);
alter table cart add constraint uk_cart_member unique (member_id);
alter table cart_item add constraint uk_cart_item unique (cart_id, product_id);
alter table coupon add constraint uk_coupon_code unique (code);
alter table genre add constraint uk_genre_name unique (name);
create index idx_limited_drop_status_open on limited_drop (status, open_at);
alter table limited_drop add constraint uk_limited_drop_product unique (product_id);
alter table limited_purchase add constraint uk_limited_purchase unique (drop_id, member_id);
alter table member add constraint uk_member_email unique (email);
alter table member_coupon add constraint uk_member_coupon_member_coupon unique (member_id, coupon_id);
create index idx_order_item_order on order_item (order_id);
create index idx_order_item_product on order_item (product_id);
create index idx_orders_member_created on orders (member_id, created_at);
create index idx_orders_status on orders (status);
create index idx_orders_status_expires on orders (status, expires_at);
alter table orders add constraint uk_orders_order_number unique (order_number);
create index idx_payment_approved_at on payment (approved_at);
alter table payment add constraint uk_payment_order unique (order_id);
alter table payment add constraint uk_payment_key unique (payment_key);
alter table payment add constraint uk_payment_toss_order_id unique (toss_order_id);
create index idx_product_title_artist on product (title, artist_id);
create index idx_product_status_created on product (status, created_at);
create index idx_product_artist on product (artist_id);
create index idx_product_label on product (label_id);
create index idx_product_genre_genre on product_genre (genre_id);
alter table product_genre add constraint uk_product_genre unique (product_id, genre_id);
create index idx_product_image_product on product_image (product_id, sort_order);
create index idx_review_product on review (product_id, created_at);
alter table review add constraint uk_review_product_member unique (product_id, member_id);
alter table stock add constraint uk_stock_product unique (product_id);
create index idx_stock_history_stock on stock_history (stock_id, created_at);
alter table wishlist add constraint uk_wishlist_member_product unique (member_id, product_id);

-- 외래키
alter table address add constraint fk_address_member foreign key (member_id) references member (id);
alter table admin_audit_log add constraint fk_audit_log_admin foreign key (admin_id) references member (id);
alter table cart add constraint fk_cart_member foreign key (member_id) references member (id);
alter table cart_item add constraint fk_cart_item_cart foreign key (cart_id) references cart (id);
alter table cart_item add constraint fk_cart_item_product foreign key (product_id) references product (id);
alter table limited_drop add constraint fk_limited_drop_product foreign key (product_id) references product (id);
alter table limited_purchase add constraint fk_limited_purchase_drop foreign key (drop_id) references limited_drop (id);
alter table limited_purchase add constraint fk_limited_purchase_member foreign key (member_id) references member (id);
alter table limited_purchase add constraint fk_limited_purchase_order foreign key (order_id) references orders (id);
alter table member_coupon add constraint fk_member_coupon_coupon foreign key (coupon_id) references coupon (id);
alter table member_coupon add constraint fk_member_coupon_member foreign key (member_id) references member (id);
alter table order_item add constraint fk_order_item_order foreign key (order_id) references orders (id);
alter table order_item add constraint fk_order_item_product foreign key (product_id) references product (id);
alter table orders add constraint fk_orders_member foreign key (member_id) references member (id);
alter table orders add constraint fk_orders_member_coupon foreign key (member_coupon_id) references member_coupon (id);
alter table payment add constraint fk_payment_order foreign key (order_id) references orders (id);
alter table product add constraint fk_product_artist foreign key (artist_id) references artist (id);
alter table product add constraint fk_product_label foreign key (label_id) references label (id);
alter table product_genre add constraint fk_pg_genre foreign key (genre_id) references genre (id);
alter table product_genre add constraint fk_pg_product foreign key (product_id) references product (id);
alter table product_image add constraint fk_product_image_product foreign key (product_id) references product (id);
alter table review add constraint fk_review_member foreign key (member_id) references member (id);
alter table review add constraint fk_review_product foreign key (product_id) references product (id);
alter table stock add constraint fk_stock_product foreign key (product_id) references product (id);
alter table stock_history add constraint fk_stock_history_stock foreign key (stock_id) references stock (id);
alter table wishlist add constraint fk_wishlist_member foreign key (member_id) references member (id);
alter table wishlist add constraint fk_wishlist_product foreign key (product_id) references product (id);
