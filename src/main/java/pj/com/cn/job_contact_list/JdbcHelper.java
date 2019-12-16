package pj.com.cn.job_contact_list;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;
import pj.com.cn.job_contact_list.model.CallResult;

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
		CallResult<List<JsonObject>> result = new CallResult<List<JsonObject>>();
		try {
			client.getConnection(cr -> {
				if (cr.succeeded()) {
					SQLConnection connection = cr.result();
					if (connection != null) {
						connection.queryWithParams(sql, params, qr -> {
							if (qr.succeeded()) {
								res.end(result.ok(qr.result().getRows()).toString());
							} else {
								res.end(result.err().toString());
							}
							connection.close();
						});
					} else {
						res.end(result.err("the DB connect is null.").toString());
					}
				} else {
					res.end(result.err("get DB connect err.").toString());
				}
			});
		} catch (Exception e) {
			res.end(result.err(e.getMessage()).toString());
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
		CallResult<JsonObject> result = new CallResult<JsonObject>();
		try {
			client.getConnection(cr -> {
				if (cr.succeeded()) {
					SQLConnection connection = cr.result();
					if (connection != null) {
						connection.queryWithParams(sql, params, qr -> {
							if (qr.succeeded()) {
								res.end(result.ok(qr.result().getRows().get(0)).toString());
							} else {
								res.end(result.err().toString());
							}
							connection.close();
						});
					} else {
						res.end(result.err("the DB connect is null.").toString());
					}
				} else {
					res.end(result.err("get DB connect err.").toString());
				}
			});
		} catch (Exception e) {
			res.end(result.err(e.getMessage()).toString());
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
		CallResult<List<JsonObject>> result = new CallResult<List<JsonObject>>();
		try {
			client.getConnection(cr -> {
				if (cr.succeeded()) {
					SQLConnection connection = cr.result();
					if (connection != null) {
						connection.updateWithParams(sql, params, qr -> {
							if (qr.succeeded()) {
								res.end(result.ok().toString());
							} else {
								res.end(result.err().toString());
							}
							connection.close();
						});
					} else {
						res.end(result.err("the DB connect is null.").toString());
					}
				} else {
					res.end(result.err("get DB connect err.").toString());
				}
			});
		} catch (Exception e) {
			res.end(result.err(e.getMessage()).toString());
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
