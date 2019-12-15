package pj.com.cn.job_contact_list.request_handler;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;
import pj.com.cn.job_contact_list.ConfigVerticle;
import pj.com.cn.job_contact_list.JdbcHelper;
import pj.com.cn.job_contact_list.Utils;
import pj.com.cn.job_contact_list.model.CallResult;

/**
 * @author PJ
 * @version 创建时间：2019年6月19日 下午8:53:30 消息通知
 */
public class NotifyHandler {

	/**
	 * 用户消息列表
	 */
	public void handleNotify(RoutingContext ctx) {
		JsonObject rp = ctx.getBodyAsJson();
		String sql = "select a.*,b.contactType from contact_notify a,contact b " + //
				" where a.notifyuser = ? and a.read = ? and a.contactId = b.contactId " + //
				" order by notifydate desc";
		JsonArray params = new JsonArray().add(rp.getString("notifyUser")).add(rp.getString("read"));
		JdbcHelper.rows(ctx, sql, params);
	}

	/**
	 * 消息已阅
	 */
	public void readMsg(RoutingContext ctx) {
		JsonObject rp = ctx.getBodyAsJson();
		String sql = "update contact_notify set read = 'Y' where notifyuser = ? and read = 'N'";
		JsonArray params = new JsonArray().add(rp.getString("notifyUser"));
		JdbcHelper.update(ctx, sql, params);
	}

	/**
	 * 联系单状态改变消息 <br>
	 * 注意,这种调用方式未加入串联,对返回结果也并未处理
	 */
	public void sendMsg(int contactId, String message) {
		CallResult<String> result = new CallResult<String>();
		SQLClient client = ConfigVerticle.client;
		client.getConnection(res -> {
			if (res.succeeded()) {
				SQLConnection connection = res.result();
				// 1.查找联系单，获得干系人
				String sql = "select checkinuser||','||linkman||','||iter as projectman from " + //
						" contact where contactid = ?";
				Future<String> f1 = Future.future(promise -> {
					JsonArray params = new JsonArray().add(contactId);
					connection.querySingleWithParams(sql, params, r -> {
						if (r.succeeded()) {
							promise.complete(r.result().getString(0));// 人员
						} else {
							promise.fail("query contact by id error.");
						}
					});
				});
				// 2.发消息给干系人
				f1.compose(r -> {
					String[] mans = r.split(",");
					List<String> projectMans = new ArrayList<String>(Arrays.asList(mans));
					List<JsonArray> pms = projectMans.stream().filter(m -> !m.isEmpty() && !"NULL".equals(m))
							.map(m -> new JsonArray()//
									.add(UUID.randomUUID().toString())// notifyId
									.add(contactId)// contactId
									.add(message)// content
									.add(m)// notifyUser
									.add(Utils.getUTCTimeStr())// notifyDate
									.add("N"))// read
							.collect(toList());
					Future<Object> f2 = Future.future(promise -> {
						connection.batchWithParams("insert into contact_notify(notifyId,contactId,"
								+ "content,notifyUser,notifyDate,read) values(?,?,?,?,?,?) ", pms, ur -> {
							if (ur.succeeded()) {
								promise.complete();
							} else {
								promise.fail("insert notify error.");
							}
						});
					});
					return f2;
				}).setHandler(fr -> {
					if (fr.succeeded()) {
						result.ok();
					} else {
						result.err(fr.result().toString());
					}
					connection.close();
				});
			}
		});
	}

	/**
	 * 保存公告消息 <br>
	 * 公告消息包括系统自动生成公告,手动发布公告<br>
	 * 统一由客户端调用
	 */
	public void saveBroadcast(RoutingContext ctx) {
		JsonObject rp = ctx.getBodyAsJson();
		String sql = "insert into contact_broadcast(MSGID,CONTACTID,TITLE," //
				+ "CONTENT,MSGLEVEL,MSGTYPE,MSGRANGE,PUBLISHUSER,PUBLISHDATE,EXPIRATIONTIME,VALID) values("//
				+ "?,?,?,?,?,?,?,?,?,?,?)";
		JsonArray params = new JsonArray()//
				.add(UUID.randomUUID().toString())// MSGID
				.add(rp.getInteger("contactId"))// CONTACTID
				.add(rp.getString("title"))// TITLE
				.add(rp.getString("content"))// CONTENT
				.add(rp.getInteger("publishLevel"))// MSGLEVEL
				.add(rp.getString("publishType"))// MSGTYPE
				.add(rp.getInteger("publishRange"))// MSGRANGE
				.add(rp.getString("publishUser"))// PUBLISHUSER
				.add(Utils.getUTCTimeStr())// PUBLISHDATE
				.add(rp.getString("expirationTime"))// EXPIRATIONTIME
				.add(rp.getString("valid"));// VALID
		JdbcHelper.update(ctx, sql, params);
	}

	/**
	 * 公告消息列表
	 */
	public void broadcastList(RoutingContext ctx) {
		JsonObject rp = ctx.getBodyAsJson();
		String range = rp.getString("range");
		String subSql = " 1 = 1 ";
		if ("ALL".equals(range)) {
			subSql = " msgrange in (1,2,4) ";
		} else if ("PRD".equals(range)) {
			subSql = " msgrange in (2,4) ";
		} else if ("OFFICE".equals(range)) {
			subSql = " msgrange in (1,4) ";
		}
		subSql += " and (expirationtime >= " + Utils.getDBTimeStr() + " or expirationtime is null) ";
		subSql += " and valid = 'Y'";
		JsonArray params = new JsonArray().add(rp.getInteger("rownum"));
		String sql = "select * from (select * from contact_broadcast where " + subSql
				+ " order by publishdate desc) a where rownum <= ?";
		JdbcHelper.rows(ctx, sql, params);
	}

}
