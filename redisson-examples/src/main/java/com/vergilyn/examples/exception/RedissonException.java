package com.vergilyn.examples.exception;


public class RedissonException extends RuntimeException{

    public RedissonException(String message) {
        super(message);
    }

    public RedissonException(Throwable throwable){
        super(throwable);
    }
}
