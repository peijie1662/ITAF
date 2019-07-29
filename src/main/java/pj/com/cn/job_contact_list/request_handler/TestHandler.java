package pj.com.cn.job_contact_list.request_handler;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.RoutingContext;

/**
 * @author PJ
 * @version 创建时间：2019年7月15日 下午7:56:49 类说明
 */
public class TestHandler {

	private final Vertx vertx;

	public TestHandler(Vertx vertx) {
		this.vertx = vertx;
	}
	
	public void interceptor1(RoutingContext ctx){
		System.out.println("hahahaha1");

			ctx.next();
			
	}
	
	public void interceptor(RoutingContext ctx){
		System.out.println("hahahaha0");

			ctx.next();
		
	}

	public void test(RoutingContext ctx) {
		// 1.create token
		JWTAuthOptions config = new JWTAuthOptions()
				.addPubSecKey(new PubSecKeyOptions().setAlgorithm("HS256").setPublicKey("nb7432ct").setSymmetric(true));
		JWTAuth provider = JWTAuth.create(vertx, config);
		JsonObject claims = new JsonObject().put("userId", "hehe");
		String token = provider.generateToken(claims);
		System.out.println("token:" + token);
		// 2.verify token
		provider.authenticate(new JsonObject().put("jwt", token), r -> {
			System.out.println("verify result:" + r.result());
			if(r.succeeded()){
				System.out.println("verify ok!");
			}else{
				System.out.println("verify error!");
			}
		});
		HttpServerResponse res = ctx.response();
		res.end("hehe");
	}

}
