package netty.server.core.engine;

import java.io.*;
import java.util.*;

import freemarker.core.*;
import freemarker.template.*;

public class FreeMarkerTemp {

	static Configuration configuration = new Configuration(Configuration.VERSION_2_3_27);
	
	static {
		// 设置模板路径
		configuration.setClassForTemplateLoading(FreeMarkerTemp.class, "/");
		// 设置默认字体
		configuration.setDefaultEncoding("utf-8");
	}
	
	public static String get(String page, Map<String, Object> map) throws TemplateNotFoundException,
			MalformedTemplateNameException, ParseException, IOException, TemplateException {
		// 获取模板
		Template template = configuration.getTemplate(page);

		StringWriter sw = new StringWriter();
		template.process(map, sw);

		return sw.toString();
	}
}