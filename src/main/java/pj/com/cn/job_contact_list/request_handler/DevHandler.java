package pj.com.cn.job_contact_list.request_handler;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;
import pj.com.cn.job_contact_list.ConfigVerticle;
import pj.com.cn.job_contact_list.JdbcHelper;
import static pj.com.cn.job_contact_list.model.CallResult.*;

/**
 * @author PJ
 * @version 创建时间：2019年6月14日 上午9:53:52 开发联系单处理
 */
public class DevHandler {

	private NotifyHandler notifyHandler;

	public DevHandler setNotifyHandler(NotifyHandler notifyHandler) {
		this.notifyHandler = notifyHandler;
		return this;
	}

	/**
	 * 开发工作联系单列表
	 */
	public void handleDevContacts(RoutingContext ctx) {
		JsonObject rp = ctx.getBodyAsJson();
		String sql = "select * from contact where contacttype = 'DEV' and nvl(indt,' ') <> 'Y'";
		// 1.联系单编号
		String contactId = rp.getString("contactId");
		if (!contactId.isEmpty()) {
			sql += " and contactid='" + contactId + "'";
		}
		// 2.联系单状态
		String status = rp.getString("status");
		if (!status.isEmpty()) {
			sql += " and status in (" + status + ")";
		}
		// 3.文本内容
		String searchText = rp.getString("searchText");
		if (!searchText.isEmpty()) {
			sql += " and content like '%" + searchText + "%'";
		}
		// 查询
		sql += " order by checkindate desc";
		JdbcHelper.rows(ctx, sql);
	}

	/**
	 * 财务相关列表
	 */
	public void handleFinLinkContacts(RoutingContext ctx) {
		JsonObject rp = ctx.getBodyAsJson();
		String range = rp.getString("range");
		String subSql = "";
		if ("NOTCHECK".equals(range)) {
			subSql = " nvl(finlink,' ') = 'N' ";
		} else if ("CHECKED".equals(range)) {
			subSql = " nvl(finlink,' ') = 'Y' ";
		} else {
			subSql = " nvl(finlink,' ') <> ' ' ";
		}
		String sql = "select * from contact where " + subSql
				+ " and contacttype = 'DEV' and nvl(indt,' ') <> 'Y' order by checkindate desc";
		JdbcHelper.rows(ctx, sql);
	}

	/**
	 * 领导审批
	 */
	public void handleFinLink(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject rp = ctx.getBodyAsJson();
		int contactId = rp.getInteger("contactId");
		SQLClient client = ConfigVerticle.client;
		client.getConnection(cr -> {
			if (cr.succeeded()) {
				SQLConnection connection = cr.result();
				// 1.审批
				Supplier<Future<String>> updatef = () -> {
					Future<String> f = Future.future(promise -> {
						String sql = "update contact set finlink = 'Y' where contactId = ?";
						JsonArray params = new JsonArray().add(contactId);
						connection.updateWithParams(sql, params, r -> {
							if (r.succeeded()) {
								promise.complete();
							} else {
								promise.fail("fail to update contact.");
							}
						});
					});
					return f;
				};
				// 2.日志
				Supplier<Future<String>> logf = () -> {
					Future<String> f = Future.future(promise -> {
						String sql = "insert into contact_log(logId,contactId,status,statusdesc," //
								+ "tag1,tagcontent1,operator,operationdate) values(?,?,?,?,?,?,?,?)";
						JsonArray params = new JsonArray();
						params.add(UUID.randomUUID().toString());
						params.add(contactId);
						params.add("APPROVAL");
						params.add("IT工作联系单已领导审批同意.");
						params.add("领导审批");
						params.add(rp.getString("linkReply"));
						params.add(rp.getString("approvalUser"));
						params.add(rp.getString("approvalDate"));
						connection.updateWithParams(sql, params, r -> {
							if (r.succeeded()) {
								promise.complete();
							} else {
								promise.fail("fail to save log.");
							}
						});
					});
					return f;
				};
				// 3.EXCUTE
				updatef.get().compose(r -> {
					return logf.get();
				}).setHandler(r -> {
					if (r.succeeded()) {
						String message = "联系单" + rp.getInteger("contactId") + "通过领导审批。";
						notifyHandler.sendMsg(rp.getInteger("contactId"), message);
						res.end(OK());
					} else {
						res.end(Err(r.cause().getMessage()));
					}
					connection.close();
				});
			} else {
				res.end(Err("fail to get db connection."));
			}
		});
	}

	/**
	 * 首页-开发上线 列表<br>
	 * 最近的10条，倒序
	 */
	public void onlineList(RoutingContext ctx) {
		String sql = " select * from ( " + //
				"select a.*,'用户'||checkinuser||'的联系单'||a.contactid||'已于'||" + //
				" to_char(operationdate,'YYYY-MM-DD hh24:mi:ss')||'上线' as msg " + //
				" from contact a, contact_log b " + //
				" where a.contactid = b.contactid " + //
				" and b.status = 'ONLINE' " + //
				" and a.contacttype = 'DEV' " + //
				" and a.status = 'ONLINE' " + //
				" order by operationdate desc ) aa where rownum <= 5 ";
		JdbcHelper.rows(ctx, sql);
	}

}
