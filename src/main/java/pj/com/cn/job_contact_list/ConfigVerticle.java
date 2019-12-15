package pj.com.cn.job_contact_list;

import java.io.File;
import java.io.FileInputStream;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLClient;

/**
 * @author PJ
 * @version 创建时间：2019年4月10日 上午10:27:37 系统设置
 */
@SuppressWarnings("resource")
public class ConfigVerticle extends AbstractVerticle{

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

	public static SQLClient client;
	
	@Override
	public void start() {
		byte[] buff = new byte[102400];
		try {
			new FileInputStream(new File("d:/jcl.json")).read(buff);
			JsonObject config = new JsonObject(new String(buff, "utf-8"));
			registerUrl = config.getJsonObject("registerUrl");
			loginServer = config.getString("loginServer");
			provider = config.getJsonObject("provider");
			uploadDir = config.getJsonObject("upload").getString("upload_dir");
			JsonObject dbConfig = config.getJsonObject("db");
			if (dbConfig == null) {
				throw new RuntimeException("没有找到指定的数据源");
			}
			client = JDBCClient.createShared(vertx, dbConfig);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("读取配置文件失败");
		}
	}

	public static int getItafPort() {
		return provider.getInteger("port");
	}

	public static String getRegisterUrl() {
		return "http://" + registerUrl.getString("ip") + ":" + registerUrl.getInteger("port");
	}

}
