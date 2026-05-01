local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil or stock <= 0 then
    return 0
end
redis.call('DECR', KEYS[1])
return 1