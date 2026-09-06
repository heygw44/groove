-- product.country 가 varchar(2)라 Discogs 릴리즈의 country("Europe", "Germany", "UK & Europe" 같은 국가명 문자열)를
-- 저장하지 못해 적재가 실패한다. ISO 2자 코드로는 "Europe" 같은 비국가 값을 매핑할 수 없어 컬럼을 넓혀 Discogs 표기를 그대로 저장한다.
alter table product modify column country varchar(50);

-- 기존 2자 코드를 Discogs 표기로 정규화한다. US/UK 는 Discogs 도 그대로 쓰므로 유지한다.
update product set country = 'Japan' where country = 'JP';
update product set country = 'Germany' where country = 'DE';
update product set country = 'France' where country = 'FR';
update product set country = 'Italy' where country = 'IT';
update product set country = 'Netherlands' where country = 'NL';
update product set country = 'Canada' where country = 'CA';
update product set country = 'Australia' where country = 'AU';
update product set country = 'South Korea' where country = 'KR';
