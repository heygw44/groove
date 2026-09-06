-- 앨범(작품 단위)/프레싱(개별 SKU) 분리. product 는 앨범 하나에 속하는 프레싱으로 의미가 확장된다.
-- 기존 product 데이터는 (title, artist_id) 기준으로 album 을 만들어 album_id 를 채운 뒤 NOT NULL 로 전환한다.

create table album (created_at datetime(6) not null, discogs_master_id bigint, id bigint not null auto_increment, original_release_year integer, artist_id bigint not null, updated_at datetime(6) not null, title varchar(200) not null, description TEXT, primary key (id)) engine=InnoDB;

alter table product add column album_id bigint after title;
alter table product add column country varchar(2);
alter table product add column pressing_year integer;
alter table product add column catalog_no varchar(50);
alter table product add column catalog_no_normalized varchar(50);
alter table product add column barcode varchar(20);
-- CHECK 제약은 두지 않는다. Hibernate 6 가 enum 컬럼에 만드는 CHECK 는 ddl-auto update/validate 로 갱신되지 않아
-- 나중에 EditionType 상수를 추가할 때마다 운영 DB 에서 DROP 을 선행해야 한다. 엔티티는 @JdbcTypeCode(VARCHAR) 라 validate 에는 영향 없다.
alter table product add column edition_type varchar(20) default 'STANDARD' not null;
alter table product add column discogs_release_id bigint;

-- 기존 상품을 (title, artist_id) 로 묶어 album 을 채우고 product.album_id 를 연결한다.
insert into album (title, artist_id, original_release_year, created_at, updated_at)
select title, artist_id, min(year(release_date)), now(6), now(6)
from product
group by title, artist_id;

update product p
join album a on a.title = p.title and a.artist_id = p.artist_id
set p.album_id = a.id;

alter table product modify column album_id bigint not null;

-- 인덱스 / 유니크 제약
create index idx_album_artist on album (artist_id);
create index idx_album_title_artist on album (title, artist_id);
alter table album add constraint uk_album_discogs_master unique (discogs_master_id);
create index idx_product_album on product (album_id);
create index idx_product_barcode on product (barcode);
create index idx_product_catalog_no on product (catalog_no_normalized);
alter table product add constraint uk_product_discogs_release unique (discogs_release_id);

-- 외래키
alter table album add constraint fk_album_artist foreign key (artist_id) references artist (id);
alter table product add constraint fk_product_album foreign key (album_id) references album (id);
