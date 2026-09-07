-- 알림 도메인 테이블 2개(notification, album_watch)를 추가하고, wishlist 에 알림 수신 토글 컬럼을 더한다.
-- enum 컬럼(type)에는 CHECK 제약을 두지 않는다(V6 에서 정리한 정책과 동일, Hibernate 6 는 validate 로 갱신하지 않는다).

create table notification (id bigint not null auto_increment, member_id bigint not null, type varchar(20) not null, product_id bigint, album_id bigint, title_snapshot varchar(200) not null, read_at datetime(6), created_at datetime(6) not null, updated_at datetime(6) not null, primary key (id)) engine=InnoDB;

alter table notification add constraint fk_notification_member foreign key (member_id) references member (id);
alter table notification add constraint fk_notification_product foreign key (product_id) references product (id);
alter table notification add constraint fk_notification_album foreign key (album_id) references album (id);

create index idx_notification_member_created on notification (member_id, created_at);
create index idx_notification_member_read on notification (member_id, read_at);

create table album_watch (id bigint not null auto_increment, member_id bigint not null, album_id bigint not null, created_at datetime(6) not null, updated_at datetime(6) not null, primary key (id)) engine=InnoDB;

alter table album_watch add constraint uk_album_watch_member_album unique (member_id, album_id);
alter table album_watch add constraint fk_album_watch_member foreign key (member_id) references member (id);
alter table album_watch add constraint fk_album_watch_album foreign key (album_id) references album (id);

alter table wishlist add column alert_enabled tinyint default true not null;
