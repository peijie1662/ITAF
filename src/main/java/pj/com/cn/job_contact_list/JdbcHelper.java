package pj.com.cn.job_contact_list;

import java.text.SimpleDateFormat;
import java.util.Date;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;
import static pj.com.cn.job_contact_list.model.CallResult.*;

/**
 * @author PJ
 * @version 创建时间：2019年4月10日 下午12:37:19 类说明
 */
public class JdbcHelper {

	/**
	 * 查询数据集
	 * 
	 * @param ctx
	 * @param sql
	 */
	public static void rows(RoutingContext ctx, String sql) {
		JdbcHelper.rows(ctx, sql, null);
	}

	/**
	 * 查询数据集
	 * 
	 * @param ctx
	 * @param sql
	 * @param params
	 */
	public static void rows(RoutingContext ctx, String sql, JsonArray params) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		SQLClient client = ConfigVerticle.client;
		try {
			client.getConnection(cr -> {
				if (cr.succeeded()) {
					SQLConnection connection = cr.result();
					if (connection != null) {
						connection.queryWithParams(sql, params, qr -> {
							if (qr.succeeded()) {
								res.end(OK(qr.result().getRows()));
							} else {
								res.end(Err());
							}
							connection.close();
						});
					} else {
						res.end(Err("the DB connect is null."));
					}
				} else {
					res.end(Err("get DB connect err."));
				}
			});
		} catch (Exception e) {
			res.end(Err(e.getMessage()));
			e.printStackTrace();
		}
	}
	
	/**
	 * 查询数据集(1)
	 * 
	 * @param ctx
	 * @param sql
	 * @param params
	 */
	public static void oneRow(RoutingContext ctx, String sql, JsonArray params) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		SQLClient client = ConfigVerticle.client;
		try {
			client.getConnection(cr -> {
				if (cr.succeeded()) {
					SQLConnection connection = cr.result();
					if (connection != null) {
						connection.queryWithParams(sql, params, qr -> {
							if (qr.succeeded()) {
								res.end(OK(qr.result().getRows().get(0)));
							} else {
								res.end(Err());
							}
							connection.close();
						});
					} else {
						res.end(Err("the DB connect is null."));
					}
				} else {
					res.end(Err("get DB connect err."));
				}
			});
		} catch (Exception e) {
			res.end(Err(e.getMessage()));
			e.printStackTrace();
		}
	}	

	/**
	 * 修改记录
	 * 
	 * @param ctx
	 * @param sql
	 * @param params
	 * @return
	 */
	public static void update(RoutingContext ctx, String sql, JsonArray params) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		SQLClient client = ConfigVerticle.client;
		try {
			client.getConnection(cr -> {
				if (cr.succeeded()) {
					SQLConnection connection = cr.result();
					if (connection != null) {
						connection.updateWithParams(sql, params, qr -> {
							if (qr.succeeded()) {
								res.end(OK());
							} else {
								res.end(Err());
							}
							connection.close();
						});
					} else {
						res.end(Err("the DB connect is null."));
					}
				} else {
					res.end(Err("get DB connect err."));
				}
			});
		} catch (Exception e) {
			res.end(Err(e.getMessage()));
			e.printStackTrace();
		}
	}

	/**
	 * 转换日期字符串
	 */
	public static String toDbDate(Date dt) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String dtStr = "to_date('" + sdf.format(dt) + "','yyyy-mm-dd hh24:mi:ss')";
		return dtStr;
	}
}
