package pj.com.cn.job_contact_list.request_handler;

import static pj.com.cn.job_contact_list.model.CallResult.Err;
import static pj.com.cn.job_contact_list.model.CallResult.OK;

import java.util.Date;
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

/**
 * @author PJ
 * @version 创建时间：2019年6月14日 上午9:31:47 数据联系单处理
 */
public class DataHandler {

	private NotifyHandler notifyHandler;

	public DataHandler setNotifyHandler(NotifyHandler notifyHandler) {
		this.notifyHandler = notifyHandler;
		return this;
	}

	/**
	 * 数据工作联系单列表
	 */
	public void handleDataContacts(RoutingContext ctx) {
		JsonObject rp = ctx.getBodyAsJson();
		String sql = "select * from contact where contacttype = 'DATA' and nvl(indt,' ') <> 'Y'";
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
		// 3.箱号
		String cntrId = rp.getString("cntrId");
		if (!cntrId.isEmpty()) {
			sql += " and content like '%" + cntrId + "%'";
		}
		// 4.文本内容
		String searchText = rp.getString("searchText");
		if (!searchText.isEmpty()) {
			sql += " and content like '%" + searchText + "%'";
		}
		// 5.未完成联系单
		String completion = rp.getString("completion");
		if (completion != null && !completion.isEmpty()) {
			if ("Y".equals(completion)) {
				sql += " and status in ('ONLINE','CANCEL')";
			} else {
				sql += " and status not in ('ONLINE','CANCEL')";
			}
		}
		// 查询
		sql += " order by checkindate desc";
		sql = "select a.*,b.containerList from (" + sql + ") a,"//
				+ " (SELECT contactid,LISTAGG(cntrid, ',') WITHIN GROUP(ORDER BY contactid) AS containerList "//
				+ " from contact_cntrs group by contactid) b where a.contactid = b.contactid(+) ";
		JdbcHelper.rows(ctx, sql);
	}

	/**
	 * 财务审核列表
	 */
	public void handleFinCheckContacts(RoutingContext ctx) {
		JsonObject rp = ctx.getBodyAsJson();
		String range = rp.getString("range");
		String subSql = "";
		if ("NOTCHECK".equals(range)) {
			subSql = " nvl(infin,' ') = 'N' ";
		} else if ("CHECKED".equals(range)) {
			subSql = " nvl(infin,' ') = 'Y' ";
		} else {
			subSql = " nvl(infin,' ') <> ' ' ";
		}
		String sql = "select * from contact where " + subSql
				+ " and contacttype = 'DATA' and nvl(indt,' ') <> 'Y' order by checkindate desc";
		JdbcHelper.rows(ctx, sql);
	}

	/**
	 * 财务审核
	 */
	public void handleFinCheck(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject rp = ctx.getBodyAsJson();
		int contactId = rp.getInteger("contactId");
		String finReply = rp.getString("finReply");
		String checker = rp.getString("checker");
		SQLClient client = ConfigVerticle.client;
		client.getConnection(cr -> {
			if (cr.succeeded()) {
				SQLConnection connection = cr.result();
				// 1.审核
				Supplier<Future<String>> updatef = () -> {
					Future<String> f = Future.future(promise -> {
						String sql = "update contact set infin = 'Y' where contactId = ?";
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
								+ "tag1,tagContent1,operator,operationdate) " //
								+ "values(?,?,?,?,?,?,?," + JdbcHelper.toDbDate(new Date()) + ")";
						JsonArray params = new JsonArray();
						params.add(UUID.randomUUID().toString());
						params.add(contactId);
						params.add("FINAGREE");
						params.add("用户" + checker + "审核了IT工作联系单.");
						params.add("财务审核");
						params.add(finReply);
						params.add(checker);
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
						String message = "联系单" + contactId + "财务审核通过";
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
}
