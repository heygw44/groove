-- findLimitedDropStats 가 SOLD_OUT 드롭마다 매진 시각을 상관 서브쿼리(MAX(limited_purchase.created_at))로
-- 두 번(soldOutAt, soldOutSeconds) 계산해 행마다 같은 서브쿼리가 반복 실행된다. 이 컬럼에 비정규화해두고
-- 이후로는 recordSale()/restoreSale() 상태 전이 시점에 엔티티가 직접 채우고 지운다.
alter table limited_drop add column sold_out_at datetime(6) null;

-- 옛 CASE WHEN 은 status = 'SOLD_OUT' 일 때만 값을 냈다. 스케줄러가 마감 처리하면 status 가
-- CLOSED 로 바뀌어 매진 시각/소요 시간이 사라졌으므로, 마감 시점에 이미 매진 상태였던 드롭
-- (sold_count >= total_quantity) 도 함께 채운다. recordSale() 이 soldCount == totalQuantity
-- 순간 markSoldOut() 을 부르고 restoreSale() 로 되돌아가면 soldOutAt 도 함께 지워지므로,
-- 마감 시점에 sold_count 가 total_quantity 에 도달해 있으면 매진 상태로 끝난 드롭이다.
update limited_drop ld
set ld.sold_out_at = (select max(lp.created_at) from limited_purchase lp where lp.drop_id = ld.id)
where ld.status = 'SOLD_OUT'
   or (ld.status = 'CLOSED' and ld.sold_count >= ld.total_quantity);
