package netty.server.core.annotation;

import java.lang.annotation.*;

import netty.server.core.annotation.type.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Inherited // 可继承
public @interface WebUri {

	String value() default "";
	
	PageEngine engine() default PageEngine.None;
	
	HttpMethod method() default HttpMethod.ALL;
}