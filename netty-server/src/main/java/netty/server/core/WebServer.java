package netty.server.core;

import static netty.server.core.annotation.type.HttpMethod.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import io.netty.bootstrap.*;
import io.netty.channel.*;
import io.netty.channel.nio.*;
import io.netty.channel.socket.nio.*;
import io.netty.handler.logging.*;
import netty.server.core.annotation.*;

/**
 * Web服务启动类
 */
public final class WebServer {

	/**
	 * URL匹配映射(GET请求)
	 */
	static final Map<String, WebServerMapping> GET_MAPPING = new HashMap<String, WebServerMapping>();
	
	/**
	 * URL通配映射(GET请求)
	 */
	static final Map<String, WebServerMapping> GET_WILDCARDS = new HashMap<String, WebServerMapping>();

	/**
	 * URL匹配映射(POST请求)
	 */
	static final Map<String, WebServerMapping> POST_MAPPING = new HashMap<String, WebServerMapping>();
	
	/**
	 * URL通配映射(POST请求)
	 */
	static final Map<String, WebServerMapping> POST_WILDCARDS = new HashMap<String, WebServerMapping>();

	/**
	 * 服务启动，从配置文件读取端口信息，如果配置文件为空，默认80端口
	 */
	public static void run() throws Exception {
		String port = WebServerUtil.getProperties("server.properties", "port");
		run(port == null ? 80 : Integer.valueOf(port));
	}

	/**
	 * 服务启动，入参为端口号
	 */
	public static void run(int port) throws Exception {
		EventLoopGroup bossGroup = new NioEventLoopGroup(1);
		EventLoopGroup workerGroup = new NioEventLoopGroup();

		try {
			init(); // 初始化指定包下的所有类及子类

			ServerBootstrap b = new ServerBootstrap()
				.group(bossGroup, workerGroup)
				.channel(NioServerSocketChannel.class)
				.handler(new LoggingHandler(LogLevel.INFO))
				.childHandler(new WebServerInitializer());

			Channel ch = b.bind(port).sync().channel();

			ch.closeFuture().sync();
		} finally {
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}
	}

	/**
	 * 初始化
	 * @throws IOException
	 */
	private static void init() throws IOException {
		// 扫描指定包下的所有类及子类
		WebServerScanner scan = new WebServerScanner();
		List<String> list = scan.getFullyQualifiedClassNameList();

		for (String className : list) {
			Class<?> clazz = scan.forClassName(className);
			WebUri uri = clazz.getAnnotation(WebUri.class);

			// 如果该类没有@WebUri注解，跳过
			if (uri == null)
				continue;

			// 扫描出该类的所有方法
			Method[] methods = clazz.getDeclaredMethods();
			StringBuffer sb = new StringBuffer();
			
			for (Method method : methods) {
				WebUri second = method.getAnnotation(WebUri.class);

				// 如果该方法没有@WebUri注解，跳过
				if (second == null)
					continue;
				
				// 设置为可写
				method.setAccessible(true);

				// 存入映射实体中
				WebServerMapping mapping = new WebServerMapping(clazz, method, second.engine());
				
				// 转义为匹配和通配路径
				String match = uri.value() + second.value();
				String wildcards = sb.append("^")
						.append(match.replace("*", ".*"))
						.append("$")
						.toString();

				// 为了提升检索速度，在服务器启动时将URL映射存放在4个Map中，此方式消耗内存较大
				if (second.method() != POST) {
					if (match.indexOf("*") == -1)
						GET_MAPPING.put(match, mapping);
					else
						GET_WILDCARDS.put(wildcards, mapping);
				}

				if (second.method() != GET) {
					if (match.indexOf("*") == -1)
						POST_MAPPING.put(match, mapping);
					else
						POST_WILDCARDS.put(wildcards, mapping);
				}

				sb.setLength(0);
			}
		}
	}
}