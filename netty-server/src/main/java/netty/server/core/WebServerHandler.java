package netty.server.core;

import io.netty.channel.*;
import io.netty.handler.codec.http.*;

import static io.netty.handler.codec.http.HttpResponseStatus.*;

import java.util.*;

/**
 * Http请求统一处理，该类不需要被访问，权限为default
 */
class WebServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

	/**
	 * 通道处理，可扩展，目前只解析一层，没有filter
	 */
	protected void channelRead0(final ChannelHandlerContext ctx, final FullHttpRequest request) throws Exception {
		if (!request.decoderResult().isSuccess()) {
			WebServerUtil.sendError(ctx, BAD_REQUEST);
			return;
		}
		
		// 相当于request.getAttribute
		final Map<String, Object> attrubite = new HashMap<String, Object>();
		
		// 此处流程应为：
		// 1.进入过滤器(如果不符合条件，拦截，不继续执行)
		
		// 2.验证URL是否符合映射(如果不符合，直接报404，不继续执行)
		WebServerAnalysis analysis = WebServerAnalysis.analysis(ctx, request);
		if (analysis == null)
			return;
		
		// 3.解析入参出参并执行，将结果返回(不输出)
		final Object result = analysis.execute(attrubite);
		
		// 4.进入后置过滤器(后置过滤器不拦截，只对参数进行处理)
		
		// 5.将结果输出
		analysis.write(attrubite, result);
	}

	/**
	 * 500页面
	 */
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		if (ctx.channel().isActive())
			WebServerUtil.sendError(ctx, INTERNAL_SERVER_ERROR);
	}
}