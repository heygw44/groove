-- 추천 도메인 스키마: 취향 프로필과 조인 테이블 3종, 상품 조회 로그.
-- V1 과 같은 방식으로 Hibernate 스키마 생성 결과를 그대로 옮겼다. 손으로 고치지 말고 엔티티를 바꾼 뒤 V3 을 추가한다.

-- 테이블
create table member_taste_artist (artist_id bigint not null, profile_id bigint not null, primary key (artist_id, profile_id)) engine=InnoDB;
create table member_taste_decade (decade varchar(5) not null check (decade in ('D1960','D1970','D1980','D1990','D2000','D2010','D2020')), profile_id bigint not null, primary key (decade, profile_id)) engine=InnoDB;
create table member_taste_genre (genre_id bigint not null, profile_id bigint not null, primary key (genre_id, profile_id)) engine=InnoDB;
create table member_taste_profile (created_at datetime(6) not null, id bigint not null auto_increment, member_id bigint not null, updated_at datetime(6) not null, primary key (id)) engine=InnoDB;
create table product_view_log (id bigint not null auto_increment, member_id bigint, product_id bigint not null, viewed_at datetime(6) not null, primary key (id)) engine=InnoDB;

-- 인덱스 / 유니크 제약
create index idx_taste_artist_artist on member_taste_artist (artist_id);
create index idx_taste_genre_genre on member_taste_genre (genre_id);
alter table member_taste_profile add constraint uk_taste_profile_member unique (member_id);
create index idx_view_log_member_viewed on product_view_log (member_id, viewed_at);
create index idx_view_log_product_viewed on product_view_log (product_id, viewed_at);

-- 외래키
alter table member_taste_artist add constraint fk_taste_artist_artist foreign key (artist_id) references artist (id);
alter table member_taste_artist add constraint fk_taste_artist_profile foreign key (profile_id) references member_taste_profile (id);
alter table member_taste_decade add constraint fk_taste_decade_profile foreign key (profile_id) references member_taste_profile (id);
alter table member_taste_genre add constraint fk_taste_genre_genre foreign key (genre_id) references genre (id);
alter table member_taste_genre add constraint fk_taste_genre_profile foreign key (profile_id) references member_taste_profile (id);
alter table member_taste_profile add constraint fk_taste_profile_member foreign key (member_id) references member (id);
alter table product_view_log add constraint fk_view_log_member foreign key (member_id) references member (id);
alter table product_view_log add constraint fk_view_log_product foreign key (product_id) references product (id);
