-- KEYS = 조회할 공동구매 ZSET 키 목록
-- 키마다 ZREVRANGE ... WITHSCORES 결과(평탄 배열 [member, score, ...])를 키 순서대로 묶어 반환한다
local result = {}
for i = 1, #KEYS do
  result[i] = redis.call('ZREVRANGE', KEYS[i], 0, -1, 'WITHSCORES')
end
return result
