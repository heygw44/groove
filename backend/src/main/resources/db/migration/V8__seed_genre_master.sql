-- 장르 마스터를 10개에서 25개로 확장한다. 운영은 Flyway 로만 장르가 들어가므로 여기서 넣지 않으면 취향 설정 화면에
-- 고를 장르가 없다. SeedCatalog.GENRES(로컬 시더) 와 같은 집합이어야 하며 이미 있는 이름은 건너뛴다.
insert into genre (name, created_at, updated_at)
select x.name, now(6), now(6)
from (
	select 'Jazz' as name union all
	select 'Rock' union all
	select 'Hip-Hop' union all
	select 'K-Pop' union all
	select 'Soul' union all
	select 'Electronic' union all
	select 'Classical' union all
	select 'Folk' union all
	select 'Indie' union all
	select 'OST' union all
	select 'Funk' union all
	select 'Pop' union all
	select 'Blues' union all
	select 'Reggae' union all
	select 'Punk' union all
	select 'Metal' union all
	select 'Disco' union all
	select 'House' union all
	select 'Techno' union all
	select 'Ambient' union all
	select 'Alternative' union all
	select 'Country' union all
	select 'Latin' union all
	select 'R&B' union all
	select 'World'
) x
where not exists (select 1 from genre g where g.name = x.name);
