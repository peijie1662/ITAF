package pj.com.cn.job_contact_list;

import io.vertx.core.Vertx;

/**
* @author PJ 
* @version 创建时间：2019年4月10日 下午2:54:46
* 开发使用，打包时没有用
*/
public class Main {

	public static void main(String[] args) {
		Vertx vertx = Vertx.vertx();
		vertx.deployVerticle(new MainVerticle());
	}

}
