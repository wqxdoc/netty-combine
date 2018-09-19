package netty.server.core;

import java.lang.reflect.*;
import java.util.*;
import java.util.Map.*;

import io.netty.handler.codec.http.*;
import javassist.*;
import javassist.Modifier;
import javassist.bytecode.*;
import netty.server.core.annotation.type.*;

import static io.netty.handler.codec.http.HttpMethod.*;
import static netty.server.core.WebServer.*;

/**
 * URL映射实体
 */
class WebServerMapping {

	/**
	 * 解析URL对应的类
	 */
	final Class<?> clazz;
	
	/**
	 * 解析URL对应的方法
	 */
	final Method method;
	
	/**
	 * 该方法的参数名
	 */
	final String[] names;
	
	/**
	 * 模板引擎
	 */
	final PageEngine engine;

	WebServerMapping(final Class<?> clazz, final Method method, final PageEngine engine) {
		this.clazz = clazz;
		this.method = method;
		this.engine = engine;
		this.names = new String[method.getParameterTypes().length];

		// 使用增强反射工具，还原出参数名，在服务器启动时预处理，可以提升运行时速度
		// 这里还可以定义成静态来提升速度，由于增强反射后面不会用到，定义成静态不会被GC回收，所以定义到这里就可以了
		final ClassPool cp = ClassPool.getDefault();
		cp.insertClassPath(new ClassClassPath(clazz));
		
		try {
			Class<?>[] types = method.getParameterTypes();

			for (int i = 0; i < names.length; i++)
				names[i] = types[i].getName();
			
			final CtClass cc = cp.get(clazz.getName());
			final CtMethod cm = cc.getDeclaredMethod(method.getName(), cp.get(names));

			final MethodInfo methodInfo = cm.getMethodInfo();
			final CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
			final LocalVariableAttribute attr = (LocalVariableAttribute) codeAttribute.getAttribute(LocalVariableAttribute.tag);

			if (attr != null) {
				int pos = Modifier.isStatic(cm.getModifiers()) ? 0 : 1;
				for (int i = 0; i < names.length; i++)
					names[i] = attr.variableName(i + pos);
			}
		} catch (NotFoundException e) {
			// 原则上不会走到这里，因为这里的函数和参数都是从工程中扫描出来的，不存在找不到的情况
			e.printStackTrace();
		}
	}

	/**
	 * 根据URL以及提交方式解析出对应的Mapping
	 * 
	 * @param request
	 * @param uri
	 * @return
	 */
	static WebServerMapping get(final HttpRequest request, final String uri) {
		WebServerMapping mapping = null;

		if (request.method() == GET) {
			mapping = GET_MAPPING.get(uri); // 完全匹配
		} else if (request.method() == POST) {
			mapping = POST_MAPPING.get(uri); // 完全匹配
		}

		if (mapping != null)
			return mapping;

		if (request.method() == GET) {
			mapping = get(uri, GET_WILDCARDS); // 通配
		} else if (request.method() == POST) {
			mapping = get(uri, POST_WILDCARDS); // 通配
		}

		return mapping;
	}
	
	/**
	 * 通配URL遍历
	 * 
	 * @param uri
	 * @param mapping
	 * @return
	 */
	private static WebServerMapping get(String uri, Map<String, WebServerMapping> mapping) {
		for (Entry<String, WebServerMapping> item : mapping.entrySet())
			if (uri.matches(item.getKey()))
				return item.getValue();

		return null;
	}
}