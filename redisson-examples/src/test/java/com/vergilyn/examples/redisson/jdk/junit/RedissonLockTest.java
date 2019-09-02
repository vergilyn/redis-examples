package com.vergilyn.examples.redisson.jdk.junit;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import com.vergilyn.examples.redisson.RedissonApplication;
import com.vergilyn.examples.redisson.exception.RedissonException;
import com.vergilyn.examples.redisson.template.client.FairLockClient;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes= RedissonApplication.class)
public class RedissonLockTest {
	private Semaphore semaphore = new Semaphore(0);
	@Resource
	FairLockClient fairLockClient;

	@Test
	public void basicTest(){
		RLock rLock = fairLockClient.newInstance("test_100000086");
		rLock.lock();

		try {
			semaphore.tryAcquire(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			rLock.unlock();
		}


		RLock rLock2 = fairLockClient.newInstance("test_100000080");
		rLock2.lock();

		try {
			semaphore.tryAcquire(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}finally {
			rLock2.unlock();
		}

	}
	
	@Test
	public void test(){
		int count = 40;
		CountDownLatch latch = new CountDownLatch(count);
		
		for (int i = 0; i < count; i++) {
			new LockThread(fairLockClient, latch).start();
		}
		
		try {
			latch.await();
			System.out.println("11111111111");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testTemplate(){
		int count = 4;
		final CountDownLatch latch = new CountDownLatch(count);
		
		for (int i = 0; i < count; i++) {
			new Thread(){
				public void run(){
					Long template = fairLockClient.tryTemplate("test", 100, 30, TimeUnit.SECONDS, () -> {
						long index = Thread.currentThread().getId();
						System.out.println(index + ": begin....");

						Semaphore semaphore = new Semaphore(0);
						try {
							semaphore.tryAcquire(5, TimeUnit.SECONDS);
							System.out.println(index + ": end....");
						} catch (InterruptedException e) {
							e.printStackTrace();
						} finally {
							latch.countDown();
						}
						return index;
					});
					System.out.println(template);
				}
			}.start();
		}
		
		try {
			latch.await();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	// 异步获取锁参考
	public void asyncTemplate(){
		RLock rLock = fairLockClient.newInstance("test");
		RFuture<Boolean> booleanRFuture = rLock.tryLockAsync(10, 30, TimeUnit.SECONDS);

		// 获取锁之前, 不需要锁的逻辑代码...
		// code(忽略)

		try {
			booleanRFuture.await();
			if(booleanRFuture.get()){ // true: 成功获取锁
				// 获取锁之后的逻辑代码

				// some code

				rLock.unlock();
				// rLock.unlockAsync();

			}else{
				throw new RedissonException("获取锁失败, 可能原因: 等待获取锁超时, lock: test ");
			}

		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();

			if(rLock.isHeldByCurrentThread()){
				rLock.unlock();
			}
		}

	}
}

class LockThread extends Thread {
	
	private FairLockClient fairLockClient;
	private CountDownLatch latch;
	private Semaphore semaphore;
	
	public LockThread(FairLockClient fairLockClient, CountDownLatch latch){
		this.fairLockClient = fairLockClient;
		this.latch = latch;
		this.semaphore = new Semaphore(0);
	}
	
	@Override
	public void run() {
		long index = Thread.currentThread().getId();
		
		System.out.println(index + ": await lock....");

		RLock fairLock = fairLockClient.newInstance("test");
		System.out.println(index + ": lock....");

		try {
			semaphore.tryAcquire(5 , TimeUnit.SECONDS);
			System.out.println(index + ": unlock....");
		} catch (InterruptedException e) {
			e.printStackTrace();
		} finally {
			fairLock.unlock();
			latch.countDown();
		}
	}
	
}