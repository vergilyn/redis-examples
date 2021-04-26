package com.vergilyn.examples.commons.domain;

/**
 * SEE: spring-data-commons-2.2.5 `org.springframework.data.domain.PageRequest`
 * @author vergilyn
 * @since 2021-04-06
 */
public class PageRequest {

	private final int index;
	private final int size;

	private PageRequest(int index, int size) {
		if (index < 0) {
			throw new IllegalArgumentException("Page index must not be less than zero!");
		} else if (size < 1) {
			throw new IllegalArgumentException("Page size must not be less than one!");
		} else {
			this.index = index;
			this.size = size;
		}
	}

	public static PageRequest of() {
		return of(1, 10);
	}

	public static PageRequest of(int index, int size) {
		return new PageRequest(index, size);
	}

	public PageRequest next() {
		return of(this.index + 1, this.size);
	}

	public int getIndex() {
		return index;
	}

	public int getSize() {
		return size;
	}
}
