local key = KEYS[1]
local type = redis.call("type", key)["ok"]
if(type == "list") then
    local tmpkey = key .. "_tmp"
    redis.call("rename", key, tmpkey)
    repeat
        local item = redis.call("lpop", tmpkey)
        if (item) then
            local priority = cjson.decode(item).priority
            redis.call("zadd", key, 100 - priority, item)
        end
    until not item
    redis.call("del", tmpkey)
    return true
end
return false