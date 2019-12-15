package pj.com.cn.job_contact_list.request_handler;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import pj.com.cn.job_contact_list.JdbcHelper;

/**
 * @author PJ
 * @version 创建时间：2019年6月19日 下午2:04:03 改进联系单处理
 */
public class ImproveHandler {

	private NotifyHandler notifyHandler;

	/**
	 * 开发工作联系单列表
	 */
	public void handleImproveContacts(RoutingContext ctx) {
		JsonObject rp = ctx.getBodyAsJson();
		String sql = "select * from contact where contacttype = 'IMPROVE' and nvl(indt,' ') <> 'Y'";
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

	public NotifyHandler getNotifyHandler() {
		return notifyHandler;
	}

	public void setNotifyHandler(NotifyHandler notifyHandler) {
		this.notifyHandler = notifyHandler;
	}

}
