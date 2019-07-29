package pj.com.cn.job_contact_list.request_handler;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.web.RoutingContext;

/**
 * @author PJ
 * @version 创建时间：2019年6月19日 下午2:04:03 改进联系单处理
 */
public class ImproveHandler {

	private final JDBCClient client;
	
	private NotifyHandler notifyHandler;

	public ImproveHandler(JDBCClient client) {
		this.client = client;
	}
	
	/**
	 * 开发工作联系单列表
	 */
	public void handleImproveContacts(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject rr = new JsonObject();
		JsonObject pp = ctx.getBodyAsJson();
		try {
			String sql = "select * from contact where contacttype = 'IMPROVE' and nvl(indt,' ') <> 'Y'";
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

	public NotifyHandler getNotifyHandler() {
		return notifyHandler;
	}

	public void setNotifyHandler(NotifyHandler notifyHandler) {
		this.notifyHandler = notifyHandler;
	}

}
