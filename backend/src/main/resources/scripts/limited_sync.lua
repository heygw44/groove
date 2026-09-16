-- KEYS[1] = stock key, KEYS[2] = buyers key, KEYS[3] = pending key
-- ARGV[1] = dbRemaining, ARGV[2] = pendingCutoffMs, ARGV[3..] = DB 구매자 memberId 목록
-- DB 를 기준으로 Redis stock/buyers 를 다시 계산한다. pending 안의 신선한(cutoff 이내) 항목은
-- "진행 중인 선점"으로 인정해 재고에서 빼고 구매자에 더한다 - write() 커밋이 아직 반영되지 않은 요청을
-- 초과판매로 오판하지 않기 위해서다.
local dbRemaining = tonumber(ARGV[1])
local cutoff = tonumber(ARGV[2])

local dbSet = {}
for i = 3, #ARGV do
  dbSet[ARGV[i]] = true
end

local pending = redis.call('ZRANGE', KEYS[3], 0, -1, 'WITHSCORES')
local inFlight = {}
local leaked = 0
local cleared = 0
for i = 1, #pending, 2 do
  local member = pending[i]
  local score = tonumber(pending[i + 1])
  if dbSet[member] then
    redis.call('ZREM', KEYS[3], member)
    cleared = cleared + 1
  elseif score < cutoff then
    redis.call('ZREM', KEYS[3], member)
    leaked = leaked + 1
  else
    inFlight[member] = true
  end
end

local inFlightCount = 0
for _ in pairs(inFlight) do
  inFlightCount = inFlightCount + 1
end

local targetStock = dbRemaining - inFlightCount
if targetStock < 0 then
  targetStock = 0
end

local targetBuyers = {}
local targetBuyersCount = 0
for member in pairs(dbSet) do
  targetBuyers[member] = true
  targetBuyersCount = targetBuyersCount + 1
end
for member in pairs(inFlight) do
  if not targetBuyers[member] then
    targetBuyers[member] = true
    targetBuyersCount = targetBuyersCount + 1
  end
end

local stockRaw = redis.call('GET', KEYS[1])
local stockBefore = -1
if stockRaw then
  stockBefore = tonumber(stockRaw)
end
if stockBefore ~= targetStock then
  redis.call('SET', KEYS[1], targetStock)
end

local currentBuyers = redis.call('SMEMBERS', KEYS[2])
local buyersChanged = false
if #currentBuyers ~= targetBuyersCount then
  buyersChanged = true
else
  for _, member in ipairs(currentBuyers) do
    if not targetBuyers[member] then
      buyersChanged = true
      break
    end
  end
end

if buyersChanged then
  redis.call('DEL', KEYS[2])
  for member in pairs(targetBuyers) do
    redis.call('SADD', KEYS[2], member)
  end
end

return {stockBefore, targetStock, buyersChanged and 1 or 0, leaked, cleared}
