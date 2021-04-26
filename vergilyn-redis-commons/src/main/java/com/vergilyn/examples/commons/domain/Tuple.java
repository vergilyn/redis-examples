package com.vergilyn.examples.commons.domain;

/**
 * SEE: Nacos `com.alibaba.nacos.spring.util.Tuple`
 *
 * @author vergilyn
 * @since 2021-04-06
 */
public class Tuple<A, B>  {
	private A first;
	private B second;

	private Tuple() {
	}

	public static <A, B> Tuple<A, B> of() {
		return new Tuple<A, B>();
	}

	public static <A, B> Tuple<A, B> of(A first, B second) {
		Tuple<A, B> tuple = new Tuple<A, B>();
		tuple.setFirst(first);
		tuple.setSecond(second);
		return tuple;
	}

	public A getFirst() {
		return first;
	}

	public void setFirst(A first) {
		this.first = first;
	}

	public B getSecond() {
		return second;
	}

	public void setSecond(B second) {
		this.second = second;
	}
}
