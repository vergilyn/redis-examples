package com.vergilyn.examples.redis.usage.u0101;

import java.io.StringWriter;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vergilyn.examples.redis.usage.AbstractRedisClientTest;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.RandomUtils;

public abstract class AbstractUseIdsTests extends AbstractRedisClientTest {
	protected final Long MIN_USER_ID = 100_000_000_000_000_000L;
	// protected final Long MAX_USER_ID = 999_999_999_999_999_999L;
	protected final Long MAX_USER_ID = 399_999_999_999_999_999L;

	protected Set<Long> generator(long maxSize){

		return Stream.generate(this::generatorUserId)
				.parallel()
				.limit(maxSize)
				.collect(Collectors.toSet());
	}

	protected Long generatorUserId(){
		return RandomUtils.nextLong(MIN_USER_ID, MAX_USER_ID);
	}

	public void output(Set<Long> data) {

		final StringWriter sw = new StringWriter();
		try (final CSVPrinter printer = new CSVPrinter(sw, CSVFormat.DEFAULT)) {

			for (Long datum : data) {
				printer.printRecord(datum);
			}

			printer.flush();

			final String result = sw.toString();
			System.out.println(result);

		}catch (Exception e){
			e.printStackTrace();
		}
	}
}
