package com.vergilyn.examples.redis.utils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 *
 * @author vergilyn
 * @since 2021-04-06
 */
public class LuaScriptReadUtils {
	public static final String LUA_NOTE_PREFIX = "--";

	@SneakyThrows
	public static String getScript(String scriptName){
		ClassLoader classLoader = LuaScriptReadUtils.class.getClassLoader();
		// 可以避免路径中带`空格`无法读取的情况
		String filepath = classLoader.getResource("").toURI().getPath() + scriptName;

		try (FileInputStream input = new FileInputStream(filepath)){
			InputStreamReader reader = new InputStreamReader(input, UTF_8);
			BufferedReader bufferedReader = new BufferedReader(reader);

			String line;
			StringBuilder out = new StringBuilder();
			while (true){
				line = bufferedReader.readLine();
				if (line == null){
					break;
				}


				if (line.trim().startsWith(LUA_NOTE_PREFIX) || StringUtils.isBlank(line)){
					continue;
				}

				out.append(line).append('\n');
			}

			return out.toString();
		}
	}
}
