package com.vergilyn.examples.cache;

import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import com.google.common.collect.Maps;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.stereotype.Component;

/**
 * FIXME 2020-06-01 缓存为永久缓存，注意清理
 * @date 2020-06-01
 */
@Component
public class UserPraiseCache {
    private static final Long ZERO_LONG = 0L;

    /**
     * desc: 用户点赞行为 <br/>
     * type: HASH <br/>
     * key: user:praise:{userId} <br/>
     * field: {businessType}_{businessId} <br/>
     * value: {current-time-millis}, 负数:点踩, 正数:点赞, 0:未操作 <br/>
     * expired: 长期有效 <br/>
     */
    public static final String K_T_HASH_USER_PRAISE = "user:praise:%s";

    @Resource
    private HashOperations<String, String, Long> hashOperations;

    public boolean isPraised(Long userId, Byte businessType, Long businessId){
        Long value = hashOperations.get(keyPraise(userId), fieldPraise(businessType, businessId));
        return isPraised(value);
    }

    public Map<String, Boolean> isPraised(Long userId, List<String> fields){
        List<Long> values = hashOperations.multiGet(keyPraise(userId), fields);

        Map<String, Boolean> rs = Maps.newHashMap();
        Long value;
        for (int i = 0, len = fields.size(); i < len; i++){
            value = values.get(i);
            rs.put(fields.get(i), isPraised(value));
        }

        return rs;
    }

    public boolean isPraised(Long value){
        return value != null && ZERO_LONG.compareTo(value) != 0;
    }

    /**
     * TODO 2020-06-01 注意field个数最好不要超过 5000
     * @param userId
     * @param businessType
     * @param businessId
     * @return
     */
    public boolean doPraise(Long userId, Byte businessType, Long businessId){
        hashOperations.put(keyPraise(userId), fieldPraise(businessType, businessId), valuePraise());
        return true;
    }

    public boolean undoPraise(Long userId, Byte businessType, Long businessId){
        hashOperations.put(keyPraise(userId), fieldPraise(businessType, businessId), ZERO_LONG);
        return true;
    }

    public String keyPraise(Long userId){
        return String.format(K_T_HASH_USER_PRAISE, userId);
    }

    public String fieldPraise(Byte businessType, Long businessId){
        return businessType + CacheConstants.SEPARATOR_CHAR + businessId;
    }

    public Long valuePraise(){
        return System.currentTimeMillis();
    }
}
