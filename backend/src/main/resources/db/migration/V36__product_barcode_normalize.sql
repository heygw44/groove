-- 바코드 저장·검색 정규화 규칙 통일. 관리자 등록분은 입력값 그대로(하이픈·공백 포함) 저장돼
-- 숫자만으로 검색하면 정확일치에서 빠졌다. 기존 행도 공백·하이픈을 빼고, 빈 값은 null 로 맞춘다.
update product
set barcode = nullif(regexp_replace(barcode, '[[:space:]-]', ''), '')
where barcode is not null
	and barcode regexp '[[:space:]-]';
