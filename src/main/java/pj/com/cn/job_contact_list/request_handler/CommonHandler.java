package pj.com.cn.job_contact_list.request_handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import pj.com.cn.job_contact_list.JclConfig;
import pj.com.cn.job_contact_list.JdbcHelper;

/**
 * @author PJ
 * @version 创建时间：2019年6月14日 上午9:52:35 通用联系单处理
 */
public class CommonHandler {

	private final Vertx vertx;

	private final JDBCClient client;

	private final JdbcHelper helper;

	private NotifyHandler notifyHandler;

	private UploadHandler uploadHandler;

	public CommonHandler(Vertx vertx, JDBCClient client, JdbcHelper helper) {
		this.vertx = vertx;
		this.client = client;
		this.helper = helper;
	}

	/**
	 * 登录
	 */
	public void handleLogin(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject params = ctx.getBodyAsJson();
		JsonObject jr = new JsonObject();
		// 1.寻找登录服务
		try {
			WebClient webClient = WebClient.create(vertx);
			String regUrl = JclConfig.getRegisterUrl() + "/provider/" + JclConfig.loginServer;
			webClient.getAbs(regUrl).send(handle -> {
				if (handle.succeeded()) {
					JSONObject r = handle.result().bodyAsJson(JSONObject.class);
					if (r.getBoolean("flag")) {
						// 2.找到登录服务，尝试登录
						JSONObject provider = r.getJSONArray("data").getJSONObject(0);
						String loginUrl = "http://" + provider.getString("ip") + ":" + provider.getString("port")
								+ "/auth";
						JsonObject j = new JsonObject();
						j.put("appCode", params.getString("appCode"));
						j.put("userCode", params.getString("userName"));
						j.put("password", params.getString("password"));
						webClient.postAbs(loginUrl).sendJsonObject(j, h -> {
							if (h.succeeded()) {
								JSONObject lr = h.result().bodyAsJson(JSONObject.class);
								if (lr.getBoolean("flag")) {
									// 访问成功
									jr.put("flag", true);
									jr.put("data", JSON.parseObject(lr.getString("outMsg")));
									res.end(jr.encodePrettily());
									return;
								} else {
									JSONObject err = JSON.parseObject(lr.getString("errMsg"));
									// 访问登录服务失败
									jr.put("flag", false);
									jr.put("errMsg", "登录出错:" + err.getString("msg"));
									res.end(jr.encodePrettily());
									return;
								}
							} else {
								// 访问登录服务失败
								jr.put("flag", false);
								jr.put("errMsg", "访问登录服务出错:" + r.getString("errMsg"));
								res.end(jr.encodePrettily());
								return;
							}
						});
					} else {
						// 访问注册服务失败
						jr.put("flag", false);
						jr.put("errMsg", "访问注册出错:" + r.getString("errMsg"));
						res.end(jr.encodePrettily());
						return;
					}
				}
			});
		} catch (Exception e) {
			jr.put("flag", false);
			jr.put("errMsg", e.getMessage());
			res.end(jr.encodePrettily());
		}
	}

	/**
	 * 保存工作联系单
	 */
	public void handleSave(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject pp = ctx.getBodyAsJson(); // 页面传入参数
		JsonObject rr = new JsonObject();// 返回页面结果
		Future<Void> finFuture = Future.future();
		finFuture.setHandler(fin -> {
			if (fin.succeeded()) {
				rr.put("flag", true);
				rr.put("data", pp.getInteger("newId"));
			} else {
				rr.put("flag", false);
				rr.put("errMsg", fin.cause().getMessage());
			}
			res.end(rr.encodePrettily());
		});
		// 0 传入参数中ID不为空，说明是修改，如果ID为空，那么需要先得到ID
		int pageId = pp.getInteger("contactId");
		if (pageId <= 0) {
			// 1.1 新ID
			Future<ResultSet> getIdFuture = Future.future();// 联系单编号
			client.queryWithParams("select f_getSequ(?) as seq from dual", new JsonArray().add("LXDSEQ"), getIdFuture);
			getIdFuture.compose(r -> {
				int newId = r.getRows().get(0).getInteger("SEQ");
				pp.put("newId", newId);
				JsonArray params = new JsonArray();
				params.add(newId);
				params.add(pp.getString("contactType"));// contactType
				params.add(pp.getString("department"));// department
				params.add(pp.getString("linkman"));// linkMan
				params.add(pp.getString("phone"));// phone
				params.add(pp.getString("linkSystem"));// linkSystem
				params.add(pp.getString("contactLevel"));// contactLevel
				params.add(pp.getString("content"));// content
				params.add("CHECKIN");// status
				params.add(pp.getString("checkinUser"));// checkinUser
				params.add(pp.getString("checkinDate"));// checkinDate
				params.add(pp.getString("inFin"));// inFin
				params.add(pp.getString("finLink"));// finLink
				// 1.2 保存联系单信息
				Future<UpdateResult> saveFuture = Future.future();// 保存
				client.updateWithParams("insert into contact(contactid,contacttype,department,"//
						+ "linkman,phone,linksystem,contactlevel,content,status,checkinuser,"//
						+ "checkindate,infin,finlink) values(?,?,?,?,?,?,?,?,?,?,?,?,?)", params, saveFuture);
				return saveFuture;
			}).compose(r -> {
				List<String> sqls = new ArrayList<String>();
				String containerList = pp.getString("containerList");
				if (containerList != null) {
					String cntrs[] = containerList.split(",");
					for (String cntr : cntrs) {
						if (cntr.trim() != "") {
							sqls.add("insert into contact_cntrs(contactid,cntrid) values(" + pp.getInteger("newId")
									+ ",'" + cntr + "')");
						}
					}
				}
				// 1.3 保存箱号
				Future<List<Integer>> cntrFuture = Future.future();
				client.getConnection(con -> {
					con.result().batch(sqls, cntrFuture);
					con.result().close();
				});
				return cntrFuture;
			}).compose(r -> {
				// 1.4 保存联系单日志
				JsonArray lm = new JsonArray();
				lm.add(UUID.randomUUID().toString());
				lm.add(pp.getInteger("newId"));
				lm.add("CHECKIN");
				lm.add("用户" + pp.getString("checkinUser") + "登记了IT工作联系单.");
				lm.add("");
				lm.add("");
				lm.add(pp.getString("checkinUser"));
				lm.add(pp.getString("checkinDate"));
				Future<UpdateResult> logFuture = Future.future();
				client.updateWithParams(
						"insert into contact_log(logId,contactId,status,statusdesc," //
								+ "previouscontent,aftercontent,operator,operationdate) values(?,?,?,?,?,?,?,?)",
						lm, logFuture);
				return logFuture;
			}).compose(r -> {
				String message = "联系单" + pp.getInteger("newId") + "已创建";
				notifyHandler.sendMsg(pp.getInteger("newId"), message);
				if (!pp.getString("fileList").isEmpty()) {
					uploadHandler.fileTheDocument(pp.getString("checkinUser"), pp.getInteger("newId"),
							pp.getString("fileList"));
				}
				finFuture.complete();
			} , finFuture);
		} else {
			// 2.0 登记状态下才能修改
			JsonArray cm = new JsonArray().add(pp.getInteger("contactId"));
			Future<JsonArray> cf = Future.future();
			String cSql = "select status from contact where contactid = ?";
			client.querySingleWithParams(cSql, cm, cf);
			cf.compose(r -> {
				// 2.1 保存联系单信息
				JsonArray pm = new JsonArray();
				pm.add(pp.getString("content"));
				pm.add(pp.getString("finLink"));
				pm.add(pp.getInteger("contactId"));
				Future<UpdateResult> uf = Future.future();// 联系单内容更新
				if (!"CHECKIN".equals(r.getString(0))) {
					uf.fail("不是登记状态不能修改联系单内容。");
				} else {
					String sql = "update contact set content = ?,finlink = ? where contactid = ?";
					client.updateWithParams(sql, pm, uf);
				}
				return uf;
			}).compose(r -> {
				Future<UpdateResult> lf = Future.future();// 日志
				String originFinLink = pp.getString("originFinLink");
				String finLink = pp.getString("finLink");
				String originContent = pp.getString("originContent");
				String content = pp.getString("content");
				String statusDesc = "";
				boolean chg = false;
				if (!originFinLink.equals(finLink)) {
					statusDesc += "用户" + pp.getString("operator") + "修改了审批标记，从" + originFinLink + "改为" + finLink + "。";
					chg = true;
				}
				if (!originContent.equals(content)) {
					statusDesc += "用户" + pp.getString("operator") + "修改了工作联系单内容。";
					chg = true;
				}
				if (chg) {
					JsonArray lm = new JsonArray();
					lm.add(UUID.randomUUID().toString());
					lm.add(pp.getInteger("contactId"));
					lm.add("MODIFY");
					lm.add(statusDesc);
					lm.add(originContent);
					lm.add(content);
					lm.add(pp.getString("tag1"));
					lm.add(pp.getString("tagContent1"));
					lm.add(pp.getString("operator"));
					lm.add(pp.getString("operationDate"));
					client.updateWithParams("insert into contact_log(logId,contactId,status,statusdesc," //
							+ "previouscontent,aftercontent,tag1,tagcontent1,operator,operationdate) "//
							+ "values(?,?,?,?,?,?,?,?,?,?)", lm, lf);
				} else {
					lf.fail("没有任何变动，无需修改。");
				}
				return lf;
			}).compose(r -> {
				String message = "联系单" + pp.getInteger("contactId") + "已修改";
				notifyHandler.sendMsg(pp.getInteger("contactId"), message);
				finFuture.complete();
			} , finFuture);
		}
	}

	/**
	 * 工作联系单IT处理人员及IT备注修改
	 */
	public void handleIterAndMark(RoutingContext ctx) {
		JsonObject rr = new JsonObject();
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		try {
			JsonObject params = ctx.getBodyAsJson();
			JsonArray pm = new JsonArray();
			pm.add(params.getString("iter"));
			pm.add(params.getString("itMark"));
			pm.add(params.getInteger("contactId"));
			client.updateWithParams("update contact set iter = ?,itMark = ? where contactId = ?", pm, ar -> {
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
		}
	}

	/**
	 * 工作联系单删除
	 */
	public void handleDel(RoutingContext ctx) {
		JsonObject result = new JsonObject();
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		try {
			JsonObject params = ctx.getBodyAsJson();
			int contactId = params.getInteger("contactId");
			String delUser = params.getString("delUser");
			String delDate = params.getString("delDate");
			// 错误处理函数
			Consumer<JsonObject> failFun = fail -> {
				result.put("flag", false);
				result.put("errMsg", fail.getString("errMsg"));
				res.end(result.encodePrettily());
			};
			// 结束输出方法
			Consumer<Integer> successFun = r -> {
				result.put("flag", true);
				res.end(result.encodePrettily());
				String message = "工作联系单" + params.getInteger("contactId") + "已删除";
				notifyHandler.sendMsg(params.getInteger("contactId"), message);
			};
			// 更新方法
			Consumer<JsonObject> ufn = r -> {
				helper.exeSql("update contact set indt = 'Y',delUser = ?,delDate = ? where contactId = ?",
						new JsonArray(Arrays.asList(delUser, delDate, contactId)), successFun, failFun);
			};
			// 执行
			helper.notExpect("select * from contact_log where contactId = ? and status = 'ACCEPTED'",
					new JsonArray(Arrays.asList(contactId)), ufn, failFun, "工作联系单已被IT部受理,不能删除,请联系IT部取消");
		} catch (Exception e) {
			e.printStackTrace();
			result.put("flag", false);
			result.put("errMsg", e.getMessage());
			res.end(result.encodePrettily());
		}
	}

	/**
	 * 工作联系单受理
	 */
	public void handleAccept(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject result = new JsonObject();
		try {
			Consumer<JsonObject> failFun = fail -> { // 错误处理函数
				result.put("flag", false);
				result.put("errMsg", fail.getString("errMsg"));
				res.end(result.encodePrettily());
			};
			JsonObject params = ctx.getBodyAsJson();
			JsonArray pm = new JsonArray();
			pm.add(params.getString("iter"));
			pm.add("ACCEPTED");
			pm.add(params.getInteger("contactId"));
			helper.exeSql("update contact set iter = ?,status = ? where contactId = ?", pm, r0 -> {
				// 保存受理日志
				JsonArray lm = new JsonArray();
				lm.add(UUID.randomUUID().toString());
				lm.add(params.getInteger("contactId"));
				lm.add("ACCEPTED");
				lm.add("用户" + params.getString("operator") + "受理了IT工作联系单。目前由" + params.getString("iter") + "负责处理。");
				lm.add(params.getString("tag1"));
				lm.add(params.getString("tagContent1"));
				lm.add(params.getString("tag2"));
				lm.add(params.getString("tagContent2"));
				lm.add(params.getString("operator"));
				helper.exeSql("insert into contact_log(logId,contactId,status,statusdesc," //
						+ "tag1,tagContent1,tag2,tagContent2,operator,operationdate) " //
						+ "values(?,?,?,?,?,?,?,?,?," + helper.toDbDate(new Date()) + ")", lm, r1 -> {
					result.put("flag", true);
					res.end(result.encodePrettily());
					String message = "联系单" + params.getInteger("contactId") + "已受理";
					notifyHandler.sendMsg(params.getInteger("contactId"), message);
				} , failFun);
			} , failFun);
		} catch (Exception e) {
			e.printStackTrace();
			result.put("flag", false);
			result.put("errMsg", e.getMessage());
			res.end(result.encodePrettily());
		}
	}

	/**
	 * 工作联系单通用
	 */
	public void handleCommon(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject result = new JsonObject();
		try {
			String newStatus = "";
			// 错误处理函数
			Consumer<JsonObject> failFun = fail -> {
				result.put("flag", false);
				result.put("errMsg", fail.getString("errMsg"));
				res.end(result.encodePrettily());
			};
			JsonObject params = ctx.getBodyAsJson();
			JsonArray pm = new JsonArray();
			newStatus = params.getString("newStatus");
			pm.add(newStatus);
			pm.add(params.getInteger("contactId"));
			helper.exeSql("update contact set status = ? where contactId = ?", pm, r0 -> {
				// 保存日志
				JsonArray lm = new JsonArray();
				lm.add(UUID.randomUUID().toString());
				lm.add(params.getInteger("contactId"));
				lm.add(params.getString("newStatus"));
				lm.add("用户" + params.getString("operator") + "将IT工作联系单状态置为" + params.getString("newStatus"));
				lm.add(params.getString("tag1"));
				lm.add(params.getString("tagContent1"));
				lm.add(params.getString("tag2"));
				lm.add(params.getString("tagContent2"));
				lm.add(params.getString("operator"));
				helper.exeSql("insert into contact_log(logId,contactId,status,statusdesc," //
						+ "tag1,tagContent1,tag2,tagContent2,operator,operationdate) " //
						+ "values(?,?,?,?,?,?,?,?,?," + helper.toDbDate(new Date()) + ")", lm, r1 -> {
					result.put("flag", true);
					res.end(result.encodePrettily());
					String message = "联系单" + params.getInteger("contactId") + "状态改变为" + params.getString("newStatus");
					notifyHandler.sendMsg(params.getInteger("contactId"), message);
				} , failFun);
			} , failFun);
		} catch (Exception e) {
			e.printStackTrace();
			result.put("flag", false);
			result.put("errMsg", e.getMessage());
			res.end(result.encodePrettily());
		}
	}

	/**
	 * 工作联系单退回
	 */
	public void handleGoback(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject rr = new JsonObject();
		try {
			JsonObject pp = ctx.getBodyAsJson();
			// 1.状态改为CHECKIN
			int contactId = pp.getInteger("contactId");
			JsonArray pm = new JsonArray().add(contactId);
			String sql = "update contact set status = 'CHECKIN' where contactId = ? ";
			Future<UpdateResult> uf = Future.future();
			client.updateWithParams(sql, pm, uf);
			uf.compose(r -> {
				// 2.日志
				JsonArray lm = new JsonArray();
				lm.add(UUID.randomUUID().toString());
				lm.add(pp.getInteger("contactId"));
				lm.add("GOBACK");
				lm.add("用户" + pp.getString("operator") + "将工作联系单状态回退到登记状态。");
				lm.add(pp.getString("tag1"));
				lm.add(pp.getString("tagContent1"));
				lm.add(pp.getString("operator"));
				Future<UpdateResult> logFuture = Future.future();// 日志
				client.updateWithParams("insert into contact_log(logId,contactId,status,statusdesc," //
						+ "tag1,tagcontent1,operator,operationdate) "//
						+ "values(?,?,?,?,?,?,?," + helper.toDbDate(new Date()) + ")", lm, logFuture);
				return logFuture;
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
			e.printStackTrace();
			rr.put("flag", false);
			rr.put("errMsg", e.getMessage());
			res.end(rr.encodePrettily());
		}
	}

	/**
	 * 回收站列表
	 */
	public void handleRecycle(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject result = new JsonObject();
		try {
			JsonObject params = ctx.getBodyAsJson();
			JsonArray pm = new JsonArray();
			pm.add(params.getString("checkinUser"));
			String sql = "select * from contact where checkinUser = ? and"//
					+ " nvl(indt,' ') = 'Y' order by checkindate desc";
			helper.getRows(sql, pm, arr -> {
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
	 * 工作联系单时间线查询
	 */
	public void handleTimeline(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject result = new JsonObject();
		try {
			JsonObject params = ctx.getBodyAsJson();
			String sql = "select a.*,b.contactType from contact_log a,contact b " + //
					" where a.contactId = ? and a.contactId = b.contactId order by a.operationdate";
			JsonArray ja = new JsonArray();
			ja.add(params.getInteger("contactId"));
			helper.getRows(sql, ja, arr -> {
				result.put("flag", true);
				result.put("data", arr);
				res.end(result.encodePrettily());
			} , fail -> {
				result.put("flag", false);
				result.put("errMsg", fail.getString("errMsg"));
				res.end(result.encodePrettily());
			});
		} catch (Exception e) {
			result.put("flag", false);
			result.put("errMsg", e.getMessage());
			res.end(result.encodePrettily());
		}
	}

	/**
	 * 首页-联系单动态
	 */
	public void contactLogList(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject result = new JsonObject();
		try {
			String sql = "select * " + //
					" from (select a.*,b.contactType from contact_log a,contact b " + //
					" where a.contactId = b.contactId order by operationdate desc) a " + //
					" where rownum <= 8";
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
			result.put("flag", false);
			result.put("errMsg", e.getMessage());
			res.end(result.encodePrettily());
		}
	}

	/**
	 * 单个联系单
	 */
	public void singleContact(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject result = new JsonObject();
		try {
			JsonObject params = ctx.getBodyAsJson();
			String sql = "select * from contact where contactId = ?";
			JsonArray ja = new JsonArray();
			ja.add(params.getInteger("contactId"));
			helper.getOneRow(sql, ja, obj -> {
				result.put("flag", true);
				result.put("data", obj);
				res.end(result.encodePrettily());
			} , fail -> {
				result.put("flag", false);
				result.put("errMsg", fail.getString("errMsg"));
				res.end(result.encodePrettily());
			});
		} catch (Exception e) {
			result.put("flag", false);
			result.put("errMsg", e.getMessage());
			res.end(result.encodePrettily());
		}
	}

	public NotifyHandler getNotifyHandler() {
		return notifyHandler;
	}

	public void setNotifyHandler(NotifyHandler notifyHandler) {
		this.notifyHandler = notifyHandler;
	}

	public UploadHandler getUploadHandler() {
		return uploadHandler;
	}

	public void setUploadHandler(UploadHandler uploadHandler) {
		this.uploadHandler = uploadHandler;
	}

}
