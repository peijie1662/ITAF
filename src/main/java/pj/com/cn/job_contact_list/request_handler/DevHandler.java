package pj.com.cn.job_contact_list.request_handler;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
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
	 * 首页-开发上线 列表<br>
	 * 最近的10条，倒序
	 */
	public void onlineList(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject rr = new JsonObject();
		try {
			String sql = " select * from ( "+// 
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
