package com.vergilyn.examples.commons.utils;

import java.util.concurrent.TimeUnit;

/**
 * @author vergilyn
 * @since 2021-04-30
 */
public abstract class SafeSleep {

	public static void sleep(TimeUnit unit, long timeout){
		try {
			unit.sleep(timeout);
		} catch (InterruptedException e) {
		}
	}
}
