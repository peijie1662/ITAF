package pj.com.cn.job_contact_list;

import java.net.InetAddress;
import java.net.UnknownHostException;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import pj.com.cn.job_contact_list.ConfigVerticle;

/**
* @author PJ 
* @version 创建时间：2019年12月8日 上午11:39:43
*/
public class TaskVerticle extends AbstractVerticle{
	
	@Override
	public void start() throws Exception {
		
		JsonObject provider = ConfigVerticle.provider;
		JsonObject registerUrl = ConfigVerticle.registerUrl;
		WebClient wc = WebClient.create(vertx,
				new WebClientOptions().setIdleTimeout(2).setConnectTimeout(2000).setMaxWaitQueueSize(5));
		// 定时注册服务
		vertx.setPeriodic(5000, timerId -> {
			try {
				provider.put("ip", InetAddress.getLocalHost().getHostAddress());
				wc.post(registerUrl.getInteger("port"), registerUrl.getString("ip"), registerUrl.getString("url"))
						.timeout(1000).sendJsonObject(provider, ar -> {
					if (!ar.succeeded()) {
						ar.cause().printStackTrace();
					}
				});
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
		}); 
	}

}
