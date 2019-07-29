package pj.com.cn.job_contact_list.request_handler;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.web.RoutingContext;
import pj.com.cn.job_contact_list.Utils;

/**
 * @author PJ
 * @version 创建时间：2019年6月19日 下午8:53:30 消息通知
 */
public class NotifyHandler {

	private final JDBCClient client;

	public NotifyHandler(JDBCClient client) {
		this.client = client;
	}

	/**
	 * 用户消息列表
	 */
	public void handleNotify(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject rr = new JsonObject();
		try {
			JsonObject pp = ctx.getBodyAsJson();
			String sql = "select a.*,b.contactType from contact_notify a,contact b " + //
					" where a.notifyuser = ? and a.read = ? and a.contactId = b.contactId order by notifydate desc";
			client.queryWithParams(sql, new JsonArray(Arrays.asList(pp.getString("notifyUser"), pp.getString("read"))),
					r -> {
						if (r.succeeded()) {
							rr.put("flag", true);
							rr.put("data", r.result().getRows());
						} else {
							rr.put("flag", false);
							rr.put("errMsg", r.cause().getMessage());
						}
						res.end(rr.encodePrettily());
					});
		} catch (Exception e) {
			rr.put("flag", false);
			rr.put("errMsg", e.getMessage());
			res.end(rr.encodePrettily());
		}
	}

	/**
	 * 消息已阅
	 */
	public void readMsg(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject rr = new JsonObject();
		try {
			JsonObject pp = ctx.getBodyAsJson();
			String sql = "update contact_notify set read = 'Y' where notifyuser = ? and read = 'N'";
			client.updateWithParams(sql, new JsonArray(Arrays.asList(pp.getString("notifyUser"))), r -> {
				if (r.succeeded()) {
					rr.put("flag", true);
				} else {
					rr.put("flag", false);
					rr.put("errMsg", r.cause().getMessage());
				}
				res.end(rr.encodePrettily());
			});
		} catch (Exception e) {
			rr.put("flag", false);
			rr.put("errMsg", e.getMessage());
			res.end(rr.encodePrettily());
		}
	}

	/**
	 * 联系单状态改变消息 <br>
	 * 注意,这种调用方式未加入串联,对返回结果也并未处理
	 */
	public void sendMsg(int contactId, String message) {
		// 1.项目干系人
		Future<JsonArray> manFuture = Future.future();
		Future<List<Integer>> notifyFuture = Future.future();
		String sql = "select checkinuser||','||linkman||','||iter as projectman from contact where contactid = ?";
		client.querySingleWithParams(sql, new JsonArray(Arrays.asList(contactId)), manFuture);
		manFuture.compose(rrr -> {
			String[] mans = rrr.getString(0).split(",");
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
			// 2.保存消息
			client.getConnection(con -> {
				con.result().batchWithParams("insert into contact_notify(notifyId,contactId,"
						+ "content,notifyUser,notifyDate,read) values(?,?,?,?,?,?) ", pms, notifyFuture);
				con.result().close();
			});
		} , notifyFuture);
	}

	/**
	 * 保存公告消息 <br>
	 * 公告消息包括系统自动生成公告,手动发布公告<br>
	 * 统一由客户端调用
	 */
	public void saveBroadcast(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject rr = new JsonObject();
		try {
			JsonObject pp = ctx.getBodyAsJson();
			JsonArray pm = new JsonArray()//
					.add(UUID.randomUUID().toString())// MSGID
					.add(pp.getInteger("contactId"))// CONTACTID
					.add(pp.getString("title"))// TITLE
					.add(pp.getString("content"))// CONTENT
					.add(pp.getInteger("publishLevel"))// MSGLEVEL
					.add(pp.getString("publishType"))// MSGTYPE
					.add(pp.getInteger("publishRange"))// MSGRANGE
					.add(pp.getString("publishUser"))// PUBLISHUSER
					.add(Utils.getUTCTimeStr());// PUBLISHDATE
			String sql = "insert into contact_broadcast(MSGID,CONTACTID,TITLE," //
					+ "CONTENT,MSGLEVEL,MSGTYPE,MSGRANGE,PUBLISHUSER,PUBLISHDATE) values("//
					+ "?,?,?,?,?,?,?,?,?)";
			client.updateWithParams(sql, pm, ar -> {
				if (ar.succeeded()) {
					rr.put("flag", true);
				} else {
					rr.put("flag", false);
					rr.put("errMsg", ar.cause().getMessage());
				}
				res.end(rr.encodePrettily());
			});
		} catch (Exception e) {
			e.printStackTrace();
			rr.put("flag", false);
			rr.put("errMsg", e.getMessage());
			res.end(rr.encodePrettily());
		}
	}

	/**
	 * 公告消息列表
	 */
	public void broadcastList(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject rr = new JsonObject();
		try {
			JsonObject pp = ctx.getBodyAsJson();
			String range = pp.getString("range");
			String subSql = " 1 = 1 ";
			if ("ALL".equals(range)) {
				subSql = " msgrange in (1,2,4) ";
			} else if ("PRD".equals(range)) {
				subSql = " msgrange = 2 ";
			} else if ("OFFICE".equals(range)) {
				subSql = " msgrange = 1 ";
			}
			JsonArray pm = new JsonArray().add(pp.getInteger("rownum"));
			String sql = "select * from (select * from contact_broadcast where " + subSql
					+ "order by publishdate desc) a where rownum <= ?";
			client.queryWithParams(sql, pm, ar -> {
				if (ar.succeeded()) {
					rr.put("flag", true);
					rr.put("data", ar.result().getRows());
				} else {
					rr.put("flag", false);
					rr.put("errMsg", ar.cause().getMessage());
				}
				res.end(rr.encodePrettily());
			});
		} catch (Exception e) {
			e.printStackTrace();
			rr.put("flag", false);
			rr.put("errMsg", e.getMessage());
			res.end(rr.encodePrettily());
		}
	}

}
