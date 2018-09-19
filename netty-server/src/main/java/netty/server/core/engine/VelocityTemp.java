package netty.server.core.engine;

import java.io.*;
import java.util.*;

import org.apache.velocity.*;
import org.apache.velocity.app.*;
import org.apache.velocity.runtime.*;
import org.apache.velocity.runtime.resource.loader.*;

public class VelocityTemp {

	static VelocityEngine ve = new VelocityEngine();
	
	static {
		ve.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		ve.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
		
		ve.init();
	}
	
	public static String get(String page, Map<String, Object> map) {
		Template t = ve.getTemplate(page);
		VelocityContext ctx = new VelocityContext(map);

		StringWriter sw = new StringWriter();
		t.merge(ctx, sw);
		
		return sw.toString();
	}
}