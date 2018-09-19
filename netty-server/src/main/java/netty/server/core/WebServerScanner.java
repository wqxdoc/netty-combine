package netty.server.core;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.jar.*;

/**
 * 扫描包下所有类
 */
class WebServerScanner {

	private String root = WebServerUtil.getProperties("server.properties", "root");
	private final ClassLoader cl;

	WebServerScanner() {
		this.cl = getClass().getClassLoader();
	}

	WebServerScanner(ClassLoader cl) {
		this.cl = cl;
	}

	List<String> getFullyQualifiedClassNameList() throws IOException {
		System.out.println("开始扫描所有类");

		List<String> list = new ArrayList<String>();
		doScan(root, list);

		for (final String n : list)
			System.out.println("找到" + n);
		
		return list;
	}
	
	Class<?> forClassName(final String name) {
		URLClassLoader loader = null;
		try {
			final URL url = cl.getResource(dotToSplash(root));
			final String filePath = getRootPath(url);

			if (isJarFile(filePath)) {
				URL[] urls = new URL[] { url };
				loader = new URLClassLoader(urls);

				return loader.loadClass(name);
			}

			return Class.forName(name);
		} catch(ClassNotFoundException e) {
			// 原则上不会报这个异常，因为执行到这里的类都是从工程里扫描出来的
			e.printStackTrace();
			return null;
		} finally {
			if (loader != null)
				try{ loader.close(); } catch (IOException e) {}
		}
	}

	List<String> doScan(final String basePackage, final List<String> nameList) throws IOException {
		final String splashPath = dotToSplash(basePackage);

		final URL url = cl.getResource(splashPath);

		System.out.println("url:" + url);
		if (url == null)
			return nameList;
		
		final String filePath = getRootPath(url);

		final List<String> names;
		if (isJarFile(filePath)) {
			names = readFromJarFile(filePath, splashPath);
		} else {
			names = readFromDirectory(filePath);
		}

		for (final String name : names)
			if (isClassFile(name)) {
				if (!nameList.contains(name))
					nameList.add(isJarFile(filePath) ? splashToDot(name) : toFullyQualifiedName(name, basePackage));
			} else {
				doScan(new StringBuilder(basePackage)
						.append(basePackage.equals("") ? "" : ".")
						.append(name)
						.toString(), nameList);
			}

		return nameList;
	}

	String toFullyQualifiedName(final String shortName, final String basePackage) {
		final StringBuilder sb = new StringBuilder(basePackage);
		sb.append('.').append(trimExtension(shortName));

		return sb.toString();
	}

	List<String> readFromJarFile(final String jarPath, final String splashedPackageName) throws IOException {
		final List<String> nameList = new ArrayList<String>();

		JarInputStream jarIn = null;
		try {
			jarIn = new JarInputStream(new FileInputStream(jarPath));
			JarEntry entry = null;

			while ((entry = jarIn.getNextJarEntry()) != null) {
				final String name = entry.getName();
				if (name.startsWith(splashedPackageName) && isClassFile(name))
					nameList.add(name);
			}
		} finally {
			if (jarIn != null)
				jarIn.close();
		}

		return nameList;
	}

	List<String> readFromDirectory(final String path) {
		final File file = new File(path);
		final String[] names = file.list();

		return null == names ? null : Arrays.asList(names);
	}

	boolean isClassFile(final String name) {
		return name.endsWith(".class");
	}

	boolean isJarFile(final String name) {
		return name.endsWith(".jar");
	}

	static String getRootPath(final URL url) {
		final String fileUrl = url.getFile();
		int pos = fileUrl.indexOf('!');

		return -1 == pos ? fileUrl : fileUrl.substring(5, pos);
	}

	static String dotToSplash(final String name) {
		return name.replaceAll("\\.", "/");
	}

	static String splashToDot(final String name) {
		return trimExtension(name).replaceAll("/", "\\.");
	}

	static String trimExtension(final String name) {
		final int pos = name.indexOf('.');
		return -1 == pos ? name : name.substring(0, pos);
	}
}