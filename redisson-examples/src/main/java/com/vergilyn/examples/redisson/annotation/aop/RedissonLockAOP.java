package com.vergilyn.examples.redisson.annotation.aop;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import com.vergilyn.examples.redisson.annotation.RedissonLockAnno;
import com.vergilyn.examples.redisson.annotation.RedissonLockKey;
import com.vergilyn.examples.redisson.exception.RedissonException;

import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

/**
 * 感觉实现有问题, 逻辑: 进入方法前获取锁, 方法结束时释放锁
 */
@Component
@Aspect
public class RedissonLockAOP {
    @Resource
    RedissonClient redissonClient;

    @Around("@annotation(com.vergilyn.examples.redisson.annotation.RedissonLockAnno)")
    public Object beforeMethod(ProceedingJoinPoint pjp){
        MethodSignature methodSignature = (MethodSignature) pjp.getSignature();
        Method method = methodSignature.getMethod();

        RedissonLockAnno anno = method.getAnnotation(RedissonLockAnno.class);
        RLock rLock = null;
        try {
            if(anno == null){
                return pjp.proceed();
            }

            RedissonLockAnno.LockType type = anno.type();
            String prefix = anno.prefix();
            long leaseTime = anno.leaseTime();
            long timeout = anno.waitTime();
            TimeUnit unit = anno.unit();

            if(StringUtils.isBlank(prefix)){
                throw new RedissonException("parameter error: [prefix] don't allow null!");
            }

            String keySuffix = getKeySuffix(method, pjp.getArgs());
            String lockName = prefix + "_" + keySuffix;

            rLock = rLock(type, lockName);

            if(rLock.tryLock(timeout, leaseTime, unit)){
                Object rs = pjp.proceed();
                rLock.unlock();
                return rs;
            }

            throw new RedissonException("tryLock(...) method return: false.");

        } catch (Throwable throwable){
            throw new RedissonException(throwable);
        } finally {
            /* remark:
             *  isLock(), true -> 表示有线程持有锁
             *  isHeldByCurrentThread(), true -> 表示当前线程持有锁
             */
            if(rLock != null && rLock.isHeldByCurrentThread()){
                rLock.unlock();
            }
        }
    }

    private RLock rLock(RedissonLockAnno.LockType lockType, String lockName){
        RLock rLock;
        switch (lockType){
            case FAIR_LOCK: rLock = redissonClient.getFairLock(lockName); break;
            default: rLock = redissonClient.getLock(lockName); break;
        }

        return rLock;
    }

    private String getKeySuffix(Method method, Object[] args) throws RedissonException{
        Annotation[][] annotations = method.getParameterAnnotations();
        for (int i = 0, len = annotations.length; i < len; i++) {
            for (int j = 0; j < annotations[i].length; j++) {
                if (annotations[i][j] instanceof RedissonLockKey) {
                    RedissonLockKey fairKey = (RedissonLockKey) annotations[i][j];
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
