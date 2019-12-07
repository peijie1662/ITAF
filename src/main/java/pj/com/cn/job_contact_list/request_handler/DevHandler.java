package pj.com.cn.job_contact_list.request_handler;

import java.util.UUID;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.web.RoutingContext;

/**
 * @author PJ
 * @version 创建时间：2019年6月14日 上午9:53:52 开发联系单处理
 */
public class DevHandler {

	private final JDBCClient client;

	private NotifyHandler notifyHandler;

	public DevHandler(JDBCClient client) {
		this.client = client;
	}

	/**
	 * 开发工作联系单列表
	 */
	public void handleDevContacts(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject rr = new JsonObject();
		JsonObject pp = ctx.getBodyAsJson();
		try {
			String sql = "select * from contact where contacttype = 'DEV' and nvl(indt,' ') <> 'Y'";
			// 1.联系单编号
			String contactId = pp.getString("contactId");
			if (!contactId.isEmpty()) {
				sql += " and contactid='" + contactId + "'";
			}
			// 2.联系单状态
			String status = pp.getString("status");
			if (!status.isEmpty()) {
				sql += " and status in (" + status + ")";
			}
			// 3.文本内容
			String searchText = pp.getString("searchText");
			if (!searchText.isEmpty()) {
				sql += " and content like '%" + searchText + "%'";
			}
			// 查询
			sql += " order by checkindate desc";
			client.query(sql, r -> {
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
			e.printStackTrace();
			rr.put("flag", false);
			rr.put("errMsg", e.getMessage());
			res.end(rr.encodePrettily());
		}
	}

	/**
	 * 财务相关列表
	 */
	public void handleFinLinkContacts(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject rr = new JsonObject();
		try {
			JsonObject params = ctx.getBodyAsJson();
			String range = params.getString("range");
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
			client.query(sql, r -> {
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
	 * 领导审批
	 */
	public void handleFinLink(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject rr = new JsonObject();
		try {
			JsonObject pp = ctx.getBodyAsJson();
			int contactId = pp.getInteger("contactId");
			// 1.更新标记
			JsonArray um = new JsonArray();
			um.add(contactId);
			Future<UpdateResult> uf = Future.future();
			client.updateWithParams("update contact set finlink = 'Y' where contactId = ?", um, uf);
			uf.compose(r -> {
				JsonArray lm = new JsonArray();
				lm.add(UUID.randomUUID().toString());
				lm.add(contactId);
				lm.add("APPROVAL");
				lm.add("IT工作联系单已领导审批同意.");
				lm.add("领导审批");
				lm.add(pp.getString("linkReply"));
				lm.add(pp.getString("approvalUser"));
				lm.add(pp.getString("approvalDate"));
				Future<UpdateResult> lf = Future.future();
				client.updateWithParams("insert into contact_log(logId,contactId,status,statusdesc," //
						+ "tag1,tagcontent1,operator,operationdate) values(?,?,?,?,?,?,?,?)", lm, lf);
				return lf;
			}).setHandler(r -> {
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
	 * 首页-开发上线 列表<br>
	 * 最近的10条，倒序
	 */
	public void onlineList(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject rr = new JsonObject();
		try {
			String sql = " select * from ( " + //
					"select a.*,'用户'||checkinuser||'的联系单'||a.contactid||'已于'||" + //
					" to_char(operationdate,'YYYY-MM-DD hh24:mi:ss')||'上线' as msg " + //
					" from contact a, contact_log b " + //
					" where a.contactid = b.contactid " + //
					" and b.status = 'ONLINE' " + //
					" and a.contacttype = 'DEV' " + //
					" and a.status = 'ONLINE' " + //
					" order by operationdate desc ) aa where rownum <= 5 ";
			client.query(sql, r -> {
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
			e.printStackTrace();
			rr.put("flag", false);
			rr.put("errMsg", e.getMessage());
			res.end(rr.encodePrettily());
		}
	}

	public NotifyHandler getNotifyHandler() {
		return notifyHandler;
	}

	public void setNotifyHandler(NotifyHandler notifyHandler) {
		this.notifyHandler = notifyHandler;
	}

}
