package me.gergerapex1.serverflared.utils;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;

@SuppressWarnings("ALL")
public class ClassHelpers {
	private static volatile Method SERVER_PORT_METHOD;
	private static volatile Method SERVER_HOSTNAME_METHOD;
	public static Path normalizeToPath(Object object) {
		if (object instanceof Path) {
			return (Path) object;
		}
		if (object instanceof File) {
			return ((File) object).toPath();
		}
		throw new IllegalArgumentException(
				"Cannot convert to Path: " + object.getClass()
		);
	}
	public static String normalizeToHostname(Object server) {
		if (SERVER_HOSTNAME_METHOD == null) {
			SERVER_HOSTNAME_METHOD = resolveMethod(
					server.getClass(),
					String.class,
					"getHostname",
					"getServerHostname"
			);
		}

		try {
			return (String) SERVER_HOSTNAME_METHOD.invoke(server);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Failed to invoke server port method", e);
		}
	}
	public static int normalizeToServerPort(Object server) {

		if (SERVER_PORT_METHOD == null) {
			SERVER_PORT_METHOD = resolveMethod(
					server.getClass(),
					int.class,
					"getPort",
					"getServerPort"
			);
		}

		try {
			return (int) SERVER_PORT_METHOD.invoke(server);
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Failed to invoke server port method", e);
		}
	}
	public static Method resolveMethod(
			Class<?> clazz,
			Class<?> returnType,
			String... names
	) {
		for (String name : names) {
			try {
				Method m = clazz.getMethod(name);
				if (m.getReturnType() == returnType) {
					m.setAccessible(true);
					return m;
				}
			} catch (NoSuchMethodException ignored) {}
		}

		throw new IllegalStateException(
				"No compatible method found in " + clazz.getName()
		);
	}
}
