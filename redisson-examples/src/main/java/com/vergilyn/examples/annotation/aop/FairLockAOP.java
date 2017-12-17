package com.vergilyn.examples.annotation.aop;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import com.vergilyn.examples.annotation.FairKey;
import com.vergilyn.examples.annotation.FairLock;
import com.vergilyn.examples.exception.RedissonException;

import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Aspect
public class FairLockAOP {
    private static final Logger logger = LoggerFactory.getLogger(FairLockAOP.class);

    @Autowired
    RedissonClient redissonClient;

    @Around("@annotation(com.vergilyn.examples.annotation.FairLock)")
    public Object fairLock(ProceedingJoinPoint pjp){
        MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
        Method method = methodSignature.getMethod();

        FairLock redissonLock = method.getAnnotation(FairLock.class);
        RLock fairLock = null;
        try {
            if(redissonLock == null){
                return pjp.proceed();
            }

            String prefix = redissonLock.prefix();
            long leaseTime = redissonLock.leaseTime();
            long timeout = redissonLock.waitTime();
            TimeUnit unit = redissonLock.unit();

            if(StringUtils.isBlank(prefix)){
                throw new RedissonException("parameter error: [prefix] don't allow null!");
            }

            String fairKey = getFairKey(method, pjp.getArgs());
            String lockName = prefix + "_" + fairKey;

            fairLock = redissonClient.getFairLock(lockName);
            if(fairLock.tryLock(timeout, leaseTime, unit)){
                return pjp.proceed();
            }else{
                throw new RedissonException("tryLock(...) method return: false.");
            }
        } catch (Throwable throwable){
            logger.error("get fair-lock exception!", throwable);
            throw new RedissonException(throwable);
        } finally {
            /* remark:
             *  isLock(), true -> 表示有线程持有锁
             *  isHeldByCurrentThread(), true -> 表示当前线程持有锁
             */
            if(fairLock != null && fairLock.isHeldByCurrentThread()){
                fairLock.unlock();
            }
        }
    }

    private String getFairKey(Method method, Object[] args) throws RedissonException{
        Annotation[][] annotations = method.getParameterAnnotations();
        for (int i = 0, len = annotations.length; i < len; i++) {
            for (int j = 0; j < annotations[i].length; j++) {
                if (annotations[i][j] instanceof FairKey) {
                    FairKey fairKey = (FairKey) annotations[i][j];
                    String[] fields = fairKey.fields();
                    String field = "";
                    try {
                        if(fields.length == 0){
                            return args[i].toString();
                        }

                        StringBuffer key = new StringBuffer();
                        for (String f : fields){
                            field = f;
                            key.append(args[i].getClass().getDeclaredField(f).toString()).append("_");
                        }
                        return key.substring(0, key.length() - 1);
                    } catch (NoSuchFieldException e) {
                        e.printStackTrace();
                        throw new RedissonException(String.format("object not exists field: [%s]", field));
                    } catch (SecurityException e){
                        e.printStackTrace();
                        throw new RedissonException(e);
                    }
                }
            }
        }
        return "";
    }

}
