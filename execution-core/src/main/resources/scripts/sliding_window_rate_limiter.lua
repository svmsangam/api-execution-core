-- KEYS[1]: Rate limit bucket key (e.g., "rate_limit:user_123")
-- ARGV[1]: Current timestamp in milliseconds (now)
-- ARGV[2]: Window size in milliseconds
-- ARGV[3]: Maximum permitted capacity (limit)
-- ARGV[4]: Key TTL in seconds (for memory cleanup)

local key = KEYS[1]
local now = tonumber(ARGV[1])
local windowMs = tonumber(ARGV[2])
local capacity = tonumber(ARGV[3])
local ttlSeconds = tonumber(ARGV[4])

-- 1. Calculate the cutoff timestamp for the sliding window
local clearBefore = now - windowMs

-- 2. Remove all entries older than the current window cutoff
redis.call('ZREMRANGEBYSCORE', key, '-inf', clearBefore)

-- 3. Get the count of requests remaining in the current window
local currentCount = redis.call('ZCARD', key)

-- 4. Check if adding this request exceeds capacity
if currentCount < capacity then
    -- Add the current request timestamp to the Sorted Set
    -- Using 'now' as score, but combining 'now' + random suffix as member for millisecond precision
    local member = now .. ":" .. redis.call('INCR', key .. ":seq")
    redis.call('ZADD', key, now, member)

    -- Refresh key expiration to prevent stale data lingering
    redis.call('EXPIRE', key, ttlSeconds)

    local remaining = capacity - currentCount - 1
    -- Return array: [1 (Allowed), remaining_tokens]
    return {1, remaining}
else
    -- Return array: [0 (Denied), 0]
    return {0, 0}
end