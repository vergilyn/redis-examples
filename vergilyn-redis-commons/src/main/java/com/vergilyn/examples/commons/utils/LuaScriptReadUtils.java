package com.vergilyn.examples.commons.utils;

import java.io.BufferedReader;
import java.io.File;
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
	public static String getScript(Class<?> clazz, String scriptPath){
		File scriptFile = new File(clazz.getResource(scriptPath).toURI());
		return getScript(scriptFile);
	}

	@SneakyThrows
	public static String getScript(File scriptFile){
		try (FileInputStream input = new FileInputStream(scriptFile)){
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
