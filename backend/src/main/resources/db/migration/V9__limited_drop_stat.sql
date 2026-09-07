-- 마감된 한정반 드롭의 실패 집계 스냅샷. 진행 중인 드롭은 Redis limited:attempts:{dropId} 가 들고 있다.
create table limited_drop_stat (already_purchased_count integer default 0 not null, closed_count integer default 0 not null, not_open_count integer default 0 not null, sold_out_count integer default 0 not null, created_at datetime(6) not null, drop_id bigint not null, flushed_at datetime(6) not null, id bigint not null auto_increment, updated_at datetime(6) not null, primary key (id)) engine=InnoDB;

alter table limited_drop_stat add constraint uk_limited_drop_stat_drop unique (drop_id);
alter table limited_drop_stat add constraint fk_limited_drop_stat_drop foreign key (drop_id) references limited_drop (id);
