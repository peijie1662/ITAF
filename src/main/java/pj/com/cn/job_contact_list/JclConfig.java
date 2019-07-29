package pj.com.cn.job_contact_list;

import java.io.File;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.web.client.WebClient;

/**
 * @author PJ
 * @version 创建时间：2019年4月10日 上午10:27:37 系统设置
 */
@SuppressWarnings("resource")
public class JclConfig {

	private static JsonObject config;

	/**
	 * 登录服务URL
	 */
	public static String loginServer;

	/**
	 * 注册服务URL
	 */
	public static JsonObject registerUrl;
	
	/**
	 * 上传目录
	 */
	public static String uploadDir;

	/**
	 * 注册时提供的自描述
	 */
	public static JsonObject provider;

	/**
	 * 数据库链接
	 */
	private JDBCClient dbClient;

	static {
		byte[] buff = new byte[102400];
		try {
			new FileInputStream(new File("./jcl.json")).read(buff);
			config = new JsonObject(new String(buff, "utf-8"));
			registerUrl = config.getJsonObject("registerUrl");
			loginServer = config.getString("loginServer");
			provider = config.getJsonObject("provider");
			uploadDir = config.getJsonObject("upload").getString("upload_dir");
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("读取配置文件失败");
		}
	}

	public JclConfig(Vertx vertx, String dsName) {
		// 数据库连接池
		JsonObject dbConfig = config.getJsonObject(dsName);
		if (dbConfig == null) {
			throw new RuntimeException("没有找到指定的数据源");
		}
		dbClient = JDBCClient.createShared(vertx, dbConfig);
		// 定时注册服务
		vertx.setPeriodic(5000, timerId -> {
			try {
				provider.put("ip", InetAddress.getLocalHost().getHostAddress());
				WebClient hc = WebClient.create(vertx);
				hc.post(registerUrl.getInteger("port"), registerUrl.getString("ip"), registerUrl.getString("url"))
						.sendJsonObject(provider, ar -> {
					if (!ar.succeeded()) {
						ar.cause().printStackTrace();
					}
				});
			} catch (UnknownHostException e) {
				e.printStackTrace();
			}
		});
	}

	public JclConfig(Vertx vertx) {
		this(vertx, "db");
	}

	public JDBCClient getDbClient() {
		return dbClient;
	}

	public int getItafPort() {
		return provider.getInteger("port");
	}

	public static String getRegisterUrl() {
		return "http://" + registerUrl.getString("ip") + ":" + registerUrl.getInteger("port");
	}

}
