package pj.com.cn.job_contact_list.request_handler;

import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.function.Consumer;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import pj.com.cn.job_contact_list.JdbcHelper;

/**
 * @author PJ
 * @version 创建时间：2019年6月14日 上午9:31:47 数据联系单处理
 */
public class DataHandler {

	private  final JdbcHelper helper;
	
	private NotifyHandler notifyHandler;

	public NotifyHandler getNotifyHandler() {
		return notifyHandler;
	}

	public void setNotifyHandler(NotifyHandler notifyHandler) {
		this.notifyHandler = notifyHandler;
	}

	public DataHandler(JdbcHelper helper) {
		this.helper = helper;
	}

	/**
	 * 数据工作联系单列表
	 */
	public void handleDataContacts(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject result = new JsonObject();
		try {
			JsonObject params = ctx.getBodyAsJson();
			String sql = "select * from contact where contacttype = 'DATA' and nvl(indt,' ') <> 'Y'";
			// 1.联系单编号
			String contactId = params.getString("contactId");
			if (!contactId.isEmpty()) {
				sql += " and contactid='" + contactId + "'";
			}
			// 2.联系单状态
			String status = params.getString("status");
			if (!status.isEmpty()) {
				sql += " and status in (" + status + ")";
			}
			// 3.箱号
			String cntrId = params.getString("cntrId");
			if (!cntrId.isEmpty()) {
				sql += " and content like '%" + cntrId + "%'";
			}
			// 4.文本内容
			String searchText = params.getString("searchText");
			if (!searchText.isEmpty()) {
				sql += " and content like '%" + searchText + "%'";
			}
			// 5.未完成联系单
			String completion = params.getString("completion");
			if (completion != null && !completion.isEmpty()) {
				if ("Y".equals(completion)) {
					sql += " and status in ('ONLINE','CANCEL')";
				} else {
					sql += " and status not in ('ONLINE','CANCEL')";
				}
			}
			// 查询
			sql += " order by checkindate desc";
			sql = "select a.*,b.containerList from ("+ sql +") a,"//        
              + " (SELECT contactid,LISTAGG(cntrid, ',') WITHIN GROUP(ORDER BY contactid) AS containerList "//
              + " from contact_cntrs group by contactid) b where a.contactid = b.contactid(+) ";
			helper.getRows(sql, null, arr -> {
				result.put("flag", true);
				result.put("data", arr);
				res.end(result.encodePrettily());
			} , fail -> {
				result.put("flag", false);
				result.put("errMsg", fail.getString("errMsg"));
				res.end(result.encodePrettily());
			});
		} catch (Exception e) {
			e.printStackTrace();
			result.put("flag", false);
			result.put("errMsg", e.getMessage());
			res.end(result.encodePrettily());
		}
	}

	/**
	 * 财务审核列表
	 */
	public void handleFinCheckContacts(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject result = new JsonObject();
		try {
			JsonObject params = ctx.getBodyAsJson();
			String range = params.getString("range");
			String subSql = "";
			if ("NOTCHECK".equals(range)) {
				subSql = " nvl(infin,' ') = 'N' ";
			} else if ("CHECKED".equals(range)) {
				subSql = " nvl(infin,' ') = 'Y' ";
			} else {
				subSql = " nvl(infin,' ') <> ' ' ";
			}
			String sql = "select * from contact where " + subSql + " order by checkindate desc";
			helper.getRows(sql, null, arr -> {
				result.put("flag", true);
				result.put("data", arr);
				res.end(result.encodePrettily());
			} , fail -> {
				result.put("flag", false);
				result.put("errMsg", fail.getString("errMsg"));
				res.end(result.encodePrettily());
			});
		} catch (Exception e) {
			e.printStackTrace();
			result.put("flag", false);
			result.put("errMsg", e.getMessage());
			res.end(result.encodePrettily());
		}
	}

	/**
	 * 财务审核
	 */
	public void handleFinCheck(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject result = new JsonObject();
		try {
			// 传入参数
			JsonObject params = ctx.getBodyAsJson();
			int contactId = params.getInteger("contactId");
			String finReply = params.getString("finReply");
			String checker = params.getString("checker");
			// 错误处理函数
			Consumer<JsonObject> failFun = fail -> {
				result.put("flag", false);
				result.put("errMsg", fail.getString("errMsg"));
				res.end(result.encodePrettily());
			};
			// 返回
			Consumer<Integer> resFun = r1 -> {
				result.put("flag", true);
				res.end(result.encodePrettily());
				String message = "联系单" + params.getInteger("contactId") + "财务审核通过";
				notifyHandler.sendMsg(params.getInteger("contactId"), message);
			};
			// 保存受理日志
			Consumer<Integer> logFun = j -> {
				JsonArray lm = new JsonArray();
				lm.add(UUID.randomUUID().toString());
				lm.add(contactId);
				lm.add("FINAGREE");
				lm.add("用户" + checker + "审核了IT工作联系单.");
				lm.add("财务审核");
				lm.add(finReply);
				lm.add(checker);
				helper.exeSql("insert into contact_log(logId,contactId,status,statusdesc," //
						+ "tag1,tagContent1,operator,operationdate) " //
						+ "values(?,?,?,?,?,?,?," + helper.toDbDate(new Date()) + ")", lm, resFun, failFun);
			};
			// 更新
			String sql = "update contact set infin = 'Y' where contactId = ?";
			helper.exeSql(sql, new JsonArray(Arrays.asList(contactId)), logFun, failFun);
		} catch (Exception e) {
			result.put("flag", false);
			result.put("errMsg", e.getMessage());
			res.end(result.encodePrettily());
		}
	}
}
