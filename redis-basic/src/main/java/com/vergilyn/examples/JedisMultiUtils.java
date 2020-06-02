package com.vergilyn.examples;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

import org.apache.commons.lang3.StringUtils;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

/**
 * <p>
 *   通过{@link Pipeline Jedis.Pipeline}批量get: String、Hash、List、Set、Sort;
 *   如果需要转换结果, keys-values的结构必须相同。（具体可以返回String, 具体再处理）
 * <p/>
 * <p> 问题: <br/>
 *     1. 因为参数emptySet可能是null, 所以才有clazz; 否则可以直接从emptySet获取到T.class; <br/>
 *     2. 因为参数不一致, 导致大量的代码重复. 每个批量get只有很少一部分代码是特殊的.
 * </p>
 * @author VergiLyn
 * @blog http://www.cnblogs.com/VergiLyn/
 * @date 2018/3/11
 */
public class JedisMultiUtils {
    // FIXME 2020-06-02
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     *
     * @param keys
     * @param clazz val转换为clazz, 不支持{@link Character Character.class}
     * @param emptySet 当某个key的结果是null时, 执行emptySet;
     * @return key: redis-key, value: clazz转换的结果;
     */
    public static <T> Map<String, T> mgetString(String[] keys, Class<T> clazz, RedisMultiAbstract<T> emptySet){
        Jedis jedis = null;
        Pipeline pipelined = null;
        Map<String, T> result = Maps.newHashMap();
        List<String> reqKeys = Lists.newArrayList(keys);
        try {
            jedis = JedisUtils.getJedis();
            pipelined = jedis.pipelined();

            handleNotExistsKey(result, reqKeys, pipelined, emptySet);

            Response<List<String>> response = pipelined.mget(reqKeys.toArray(new String[]{}));

            pipelined.sync();

            List<String> values = response.get();

            for (int i = 0, len = reqKeys.size(); i < len; i++) {
                result.put(reqKeys.get(i), transResult(values.get(i), clazz));
            }

            return result;
        }catch (Exception e){
            e.printStackTrace();
        } finally {
            close(jedis, pipelined);
        }

        return null;
    }

    /**
     * @param fields null查询全部fields
     * @param emptySet
     * @return
     */
    public static Map<String, Map<String, String>> mgetHash(String[] keys, String[] fields, RedisMultiAbstract<Map<String, String>> emptySet){
        Jedis jedis = null;
        Pipeline pipelined = null;
        Map<String, Map<String, String>> result = Maps.newHashMap();
        List<String> reqKeys = Lists.newArrayList(keys);
        try {
            jedis = JedisUtils.getJedis();
            pipelined = jedis.pipelined();

            handleNotExistsKey(result, reqKeys, pipelined, emptySet);

            if(fields != null && fields.length > 0){
                Map<String, Response<List<String>>> responseMap = Maps.newHashMap();

                for(String key : reqKeys){
                    responseMap.put(key, pipelined.hmget(key, fields));
                }

                pipelined.sync();

                for (Map.Entry<String, Response<List<String>>> entrySet : responseMap.entrySet()) {
                    Map<String, String> valMap = Maps.newHashMap();
                    List<String> value = entrySet.getValue().get();

                    for(int k = 0, ken = fields.length; k < ken; k ++){
                        valMap.put(fields[k], value.get(k));
                    }

                    result.put(entrySet.getKey(), valMap);
                }

            }else{
                Map<String, Response<Map<String, String>>> responseMap = Maps.newHashMap();

                for(String key : reqKeys) {
                    responseMap.put(key, pipelined.hgetAll(key));
                }

                pipelined.sync();

                for (Map.Entry<String, Response<Map<String, String>>> entrySet : responseMap.entrySet()) {
                    result.put(entrySet.getKey(), entrySet.getValue().get());
                }
            }

            return result;
        }catch (Exception e){
            e.printStackTrace();
        } finally {
            close(jedis, pipelined);
        }

        return null;
    }

    /**
     * 注意: 用的是<code>redis.lrange(key, start, end)</code>, 自己需要注意RedisMultiAbstract返回的LIST的顺序
     * @return key: redis-key, value: redis-key对应的LIST数据结构
     */
    public static <T> Map<String, List<T>> mgetListLrange(String[] keys, int start, int end, Class<T> clazz, RedisMultiAbstract<List<T>> emptySet){
        Jedis jedis = null;
        Pipeline pipelined = null;
        Map<String, List<T>> rs = Maps.newHashMap();
        List<String> reqKeys = Lists.newArrayList(keys);
        try {
            jedis = JedisUtils.getJedis();
            pipelined = jedis.pipelined();

            handleNotExistsKey(rs, reqKeys, pipelined, emptySet);

            Map<String, Response<List<String>>> responseMap = Maps.newHashMap();
            for(String key : reqKeys) {
                responseMap.put(key, pipelined.lrange(key, start, end));
            }

            pipelined.sync();

            for (Map.Entry<String, Response<List<String>>> entrySet : responseMap.entrySet()) {
                String key = entrySet.getKey();
                List<String> values = entrySet.getValue().get();
                List<T> temp = Lists.newArrayList();
                for(String val : values){
                    temp.add(transResult(val, clazz));
                }
                rs.put(key, temp);

            }

            return rs;
        }catch (Exception e){
            e.printStackTrace();
        } finally {
            close(jedis, pipelined);
        }

        return null;
    }

    public static <T> Map<String, List<T>> mgetSet(String[] keys, Class<T> clazz, RedisMultiAbstract<List<T>> emptySet){
        Jedis jedis = null;
        Pipeline pipelined = null;
        Map<String, List<T>> rs = Maps.newHashMap();
        List<String> reqKeys = Lists.newArrayList(keys);
        try {
            jedis = JedisUtils.getJedis();
            pipelined = jedis.pipelined();

            handleNotExistsKey(rs, reqKeys, pipelined, emptySet);

            Map<String, Response<Set<String>>> responseMap = Maps.newHashMap();
            for(String key : reqKeys) {
                responseMap.put(key, pipelined.smembers(key));
            }

            pipelined.sync();

            for (Map.Entry<String, Response<Set<String>>> entrySet : responseMap.entrySet()) {
                String key = entrySet.getKey();
                Set<String> values = entrySet.getValue().get();
                List<T> temp = Lists.newArrayList();

                for(String value : values){
                    temp.add(transResult(value, clazz));
                }
                rs.put(key, temp);
            }

            return rs;
        }catch (Exception e){
            e.printStackTrace();
        } finally {
            close(jedis, pipelined);
        }

        return null;
    }

    public static <T> Map<String, List<T>> mgetSort(String[] keys, int start, int end, Class<T> clazz, RedisMultiAbstract<List<T>> emptySet){
        Jedis jedis = null;
        Pipeline pipelined = null;
        Map<String, List<T>> rs = Maps.newHashMap();
        List<String> reqKeys = Lists.newArrayList(keys);
        try {
            jedis = JedisUtils.getJedis();
            pipelined = jedis.pipelined();

            handleNotExistsKey(rs, reqKeys, pipelined, emptySet);

            Map<String, Response<Set<String>>> responseMap = Maps.newHashMap();
            for(String key : reqKeys) {
                responseMap.put(key, pipelined.zrange(key, start, end));
            }

            pipelined.sync();

            for (Map.Entry<String, Response<Set<String>>> entrySet : responseMap.entrySet()) {
                String key = entrySet.getKey();
                Set<String> values = entrySet.getValue().get();
                List<T> temp = Lists.newArrayList();
                for(String value : values){
                    temp.add(transResult(value, clazz));
                }
                rs.put(key, temp);
            }

            return rs;
        }catch (Exception e){
            e.printStackTrace();
        } finally {
            close(jedis, pipelined);
        }

        return null;
    }

    private static void close(Jedis jedis, Pipeline pipelined) {
        try {
            if(jedis != null){
                jedis.close();
            }

            if(pipelined != null){
                pipelined.close();
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private static <T> T transResult(String val, Class<T> clazz) throws JsonProcessingException {
        if(val == null || StringUtils.isBlank(val)){
            return null;
        }

        if(Integer.class.equals(clazz)){
            return (T) Integer.valueOf(val);
        }else if(Short.class.equals(clazz)){
            return (T) Short.valueOf(val);
        }else if(Long.class.equals(clazz)){
            return (T) Long.valueOf(val);
        }else if(Byte.class.equals(clazz)){
            return (T) Byte.valueOf(val);
        }else if(Float.class.equals(clazz)){
            return (T) Float.valueOf(val);
        }else if(Double.class.equals(clazz)){
            return (T) Double.valueOf(val);
        }else if(String.class.equals(clazz)){
            return (T) val;
        }else if(Boolean.class.equals(clazz)){
            return (T) Boolean.valueOf(val);
        }else{
            return OBJECT_MAPPER.readValue(val, clazz);
        }
    }

    /**
     * 当keys[index]不存在时执行{@link RedisMultiAbstract#emptySet(String)}, 将结果写入result
     */
    private static <T> void handleNotExistsKey(Map<String, T> result, List<String> reqKeys, Pipeline pipelined, RedisMultiAbstract<T> emptySet){

        if(emptySet == null){
            return ;
        }

        Map<String, Response<Boolean>> responseMap = Maps.newHashMap();
        for(String key : reqKeys) {
            responseMap.put(key, pipelined.exists(key));
        }

        pipelined.sync();

        for (Map.Entry<String, Response<Boolean>> entrySet : responseMap.entrySet()) {
            String key = entrySet.getKey();
            Boolean value = entrySet.getValue().get();
            boolean isExist = value == null ? false : value;

            if(!isExist){
                result.put(key, emptySet.emptySet(key));
                reqKeys.remove(key);
            }
        }

        pipelined.clear();

    }
}
