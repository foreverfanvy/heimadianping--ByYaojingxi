-- 锁的key
local key = KEYS[1]
-- 当前线程标识
local threadId = ARGV[1]

-- 获取锁的线程标识
local id = redis.call('get', key)
-- 判断锁的线程标识是否一致
if(id == threadId) then
    -- 删除锁
    return redis.call('del', key)
end
return 0