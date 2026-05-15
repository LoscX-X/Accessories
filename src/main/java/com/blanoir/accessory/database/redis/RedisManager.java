package com.blanoir.accessory.database.redis;

import io.lumine.mythic.bukkit.utils.network.messaging.redis.Redis;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisManager {
    private final JedisPool jedisPool;
    public RedisManager(String host,int port){
        this.jedisPool = new JedisPool(host,port);
    }
    public void set(String key,String value){
        try(Jedis jedis = jedisPool.getResource()){
            jedis.set(key,value);
        }
    }
    //获取
    public void get(String key){
        try(Jedis jedis = jedisPool.getResource()){
            jedis.get(key);
        }
    }
    public boolean exists(String key){
        try(Jedis jedis = jedisPool.getResource()){
        return jedis.exists(key);
        }
    }
    public final void delete(String key){
        try(Jedis jedis = jedisPool.getResource()){
            jedis.del(key);
        }
    }
    public void shutdonw(){
        jedisPool.close();
    }
}
