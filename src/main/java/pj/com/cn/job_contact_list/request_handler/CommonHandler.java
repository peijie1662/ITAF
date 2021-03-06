package pj.com.cn.job_contact_list.request_handler;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

import com.alibaba.fastjson.JSONObject;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sql.SQLClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.WebClient;
import pj.com.cn.job_contact_list.ConfigVerticle;
import pj.com.cn.job_contact_list.JdbcHelper;
import pj.com.cn.job_contact_list.Utils;
import static pj.com.cn.job_contact_list.model.CallResult.*;

/**
 * @author PJ
 * @version 创建时间：2019年6月14日 上午9:52:35 通用联系单处理
 */
public class CommonHandler {

	private NotifyHandler notifyHandler;

	private UploadHandler uploadHandler;

	public CommonHandler setNotifyHandler(NotifyHandler notifyHandler) {
		this.notifyHandler = notifyHandler;
		return this;
	}

	public CommonHandler setUploadHandler(UploadHandler uploadHandler) {
		this.uploadHandler = uploadHandler;
		return this;
	}

	/**
	 * 登录
	 */
	public void handleLogin(RoutingContext ctx) {
		Vertx vertx = ctx.vertx();
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject params = ctx.getBodyAsJson();
		// 1.寻找登录服务
		try {
			WebClient webClient = WebClient.create(vertx);
			String regUrl = ConfigVerticle.getRegisterUrl() + "/provider/" + ConfigVerticle.loginServer;
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
									res.end(OK(JSONObject.parseObject(lr.getString("outMsg"))));
								} else {
									res.end(Err("访问登录服务出错:" + lr.getString("errMsg")));
								}
							} else {
								res.end(Err("无法访问登录服务:" + h.cause().getMessage()));
							}
						});
					} else {
						res.end(Err("访问注册出错:" + r.getString("errMsg")));
					}
				}
			});
		} catch (Exception e) {
			res.end(Err("登录出错:" + e.getMessage()));
		}
	}

	/**
	 * 保存工作联系单
	 */
	public void handleSave(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject rp = ctx.getBodyAsJson();
		SQLClient client = ConfigVerticle.client;
		client.getConnection(cr -> {
			if (cr.succeeded()) {
				SQLConnection connection = cr.result();
				// 1.1 添加-ID
				Supplier<Future<Integer>> getIdFuture = () -> {
					Future<Integer> f = Future.future(promise -> {
						String sql = "select f_getSequ(?) as seq from dual";
						JsonArray params = new JsonArray().add("LXDSEQ");
						connection.queryWithParams(sql, params, r -> {
							if (r.succeeded()) {
								promise.complete(r.result().getRows().get(0).getInteger("SEQ"));// newId
							} else {
								promise.fail("get contact id error.");
							}
						});
					});
					return f;
				};
				// 1.2 添加-保存联系单信息
				Function<Integer, Future<Integer>> sf = newId -> {
					Future<Integer> f = Future.future(promise -> {
						String sql = "insert into contact(contactid,contacttype,department,"//
								+ "linkman,phone,linksystem,contactlevel,content,status,checkinuser,"//
								+ "checkindate,infin,finlink) values(?,?,?,?,?,?,?,?,?,?,?,?,?)";
						JsonArray params = new JsonArray();
						params.add(newId);
						params.add(rp.getString("contactType"));// contactType
						params.add(rp.getString("department"));// department
						params.add(rp.getString("linkman"));// linkMan
						params.add(rp.getString("phone"));// phone
						params.add(rp.getString("linkSystem"));// linkSystem
						params.add(rp.getString("contactLevel"));// contactLevel
						params.add(rp.getString("content"));// content
						params.add("CHECKIN");// status
						params.add(rp.getString("checkinUser"));// checkinUser
						params.add(rp.getString("checkinDate"));// checkinDate
						params.add(rp.getString("inFin"));// inFin
						params.add(rp.getString("finLink"));// finLink
						connection.updateWithParams(sql, params, r -> {
							if (r.succeeded()) {
								promise.complete(newId);
							} else {
								promise.fail("save contact error.");
							}
						});
					});
					return f;
				};
				// 1.3添加-保存箱号
				Function<Integer, Future<Integer>> cf = newId -> {
					Future<Integer> f = Future.future(promise -> {
						List<String> sqls = new ArrayList<String>();
						String containerList = rp.getString("containerList");
						if (containerList != null) {
							String cntrs[] = containerList.split(",");
							for (String cntr : cntrs) {
								if (cntr.trim() != "") {
									sqls.add("insert into contact_cntrs(contactid,cntrid) values(" + newId + ",'" + cntr
											+ "')");
								}
							}
						}
						connection.batch(sqls, r -> {
							if (r.succeeded()) {
								promise.complete(newId);
							} else {
								promise.fail("save cntrs error.");
							}
						});
					});
					return f;
				};
				// 1.4 添加-保存日志
				Function<Integer, Future<Integer>> alf = newId -> {
					Future<Integer> f = Future.future(promise -> {
						String sql = "insert into contact_log(logId,contactId,status,statusdesc," //
								+ "previouscontent,aftercontent,operator,operationdate) values(?,?,?,?,?,?,?,?)";
						JsonArray lm = new JsonArray();
						lm.add(UUID.randomUUID().toString());
						lm.add(newId);
						lm.add("CHECKIN");
						lm.add("用户" + rp.getString("checkinUser") + "登记了IT工作联系单.");
						lm.add("");
						lm.add("");
						lm.add(rp.getString("checkinUser"));
						lm.add(rp.getString("checkinDate"));
						connection.updateWithParams(sql, lm, r -> {
							if (r.succeeded()) {
								promise.complete(newId);
							} else {
								promise.fail("save log error.");
							}
						});
					});
					return f;
				};

				// 2.1修改-联系单状态
				Supplier<Future<String>> checkf = () -> {
					Future<String> f = Future.future(promise -> {
						String sql = "select status from contact where contactid = ?";
						JsonArray params = new JsonArray().add(rp.getInteger("contactId"));
						connection.querySingleWithParams(sql, params, r -> {
							if (r.succeeded()) {
								if ("CHECKIN".equals(r.result().getString(0))) {
									promise.complete();
								} else {
									promise.fail("不是登记状态不能修改联系单内容。");
								}
							}
						});
					});
					return f;
				};
				// 2.2修改-修改联系单内容
				Supplier<Future<String>> uf = () -> {
					Future<String> f = Future.future(promise -> {
						String originContactType = rp.getString("originContactType");
						String contactType = rp.getString("contactType");
						String originFinLink = rp.getString("originFinLink");
						String finLink = rp.getString("finLink");
						String originContent = rp.getString("originContent");
						String content = rp.getString("content");
						String statusDesc = "";
						boolean chg = false;
						if (!originFinLink.equals(finLink)) {
							statusDesc += "用户" + rp.getString("operator") + "修改了审批标记，从" + originFinLink + "改为" + finLink
									+ "。";
							chg = true;
						}
						if (!originContent.equals(content)) {
							statusDesc += "用户" + rp.getString("operator") + "修改了工作联系单内容。";
							chg = true;
						}
						if (!originContactType.equals(contactType)) {
							statusDesc += "用户" + rp.getString("operator") + "修改了工作联系单类别，从" + originContactType + "改为"
									+ contactType + "。";
							chg = true;
						}
						final String status = statusDesc;
						if (chg) {
							String sql = "update contact set contactType = ?,content = ?,finlink = ? where contactid = ?";
							JsonArray params = new JsonArray();
							params.add(rp.getString("contactType"));
							params.add(rp.getString("content"));
							params.add(rp.getString("finLink"));
							params.add(rp.getInteger("contactId"));
							connection.updateWithParams(sql, params, r -> {
								if (r.succeeded()) {
									promise.complete(status);
								} else {
									promise.fail("update contact error.");
								}
							});
						} else {
							promise.fail("没有任何变动，无需修改。");
						}
					});
					return f;
				};
				// 2.3修改-保存日志
				Function<String, Future<String>> ulf = statusDesc -> {
					Future<String> f = Future.future(promise -> {
						JsonArray params = new JsonArray();
						params.add(UUID.randomUUID().toString());
						params.add(rp.getInteger("contactId"));
						params.add("MODIFY");
						params.add(statusDesc);
						params.add(rp.getString("originContent"));
						params.add(rp.getString("content"));
						params.add(rp.getString("tag1"));
						params.add(rp.getString("tagContent1"));
						params.add(rp.getString("operator"));
						params.add(rp.getString("operationDate"));
						String sql = "insert into contact_log(logId,contactId,status,statusdesc," //
								+ "previouscontent,aftercontent,tag1,tagcontent1,operator,operationdate) "//
								+ "values(?,?,?,?,?,?,?,?,?,?)";
						client.updateWithParams(sql, params, r -> {
							if (r.succeeded()) {
								promise.complete();
							} else {
								promise.fail("save log error.");
							}
						});
					});
					return f;
				};
				// 0 传入参数中ID不为空，说明是修改，如果ID为空，那么需要先得到ID
				int pageId = rp.getInteger("contactId");
				if (pageId <= 0) {
					getIdFuture.get().compose(r -> {
						return sf.apply(r);
					}).compose(r -> {
						return cf.apply(r);
					}).compose(r -> {
						return alf.apply(r);
					}).setHandler(r -> {
						if (r.succeeded()) {
							String message = "联系单" + r.result() + "已创建";
							notifyHandler.sendMsg(r.result(), message);
							uploadHandler.fileTheDocument(rp.getString("checkinUser"), r.result(),
									rp.getString("fileList"));
							res.end(OK(r.result()));
						} else {
							res.end(Err(r.cause().getMessage()));
						}
						connection.close();
					});
				} else {
					checkf.get().compose(r -> {
						return uf.get();
					}).compose(r -> {
						return ulf.apply(r);
					}).setHandler(r -> {
						if (r.succeeded()) {
							String message = "联系单" + rp.getInteger("contactId") + "已修改";
							notifyHandler.sendMsg(rp.getInteger("contactId"), message);
							res.end(OK());
						} else {
							res.end(Err(r.cause().getMessage()));
						}
						connection.close();
					});
				}
			} else {
				res.end(Err("fail to get db connection."));
			}
		});

	}

	/**
	 * 工作联系单IT处理人员及IT备注修改
	 */
	public void handleIterAndMark(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject rp = ctx.getBodyAsJson();
		SQLClient client = ConfigVerticle.client;
		client.getConnection(cr -> {
			if (cr.succeeded()) {
				SQLConnection connection = cr.result();
				Integer contactId = rp.getInteger("contactId");
				String originIter = rp.getString("originIter");
				String iter = rp.getString("iter");
				String originContactType = rp.getString("originContactType");
				String contactType = rp.getString("contactType");
				// 1.修改
				Supplier<Future<String>> uf = () -> {
					Future<String> f = Future.future(promise -> {
						String sql = "update contact set contactType = ?,iter = ?,itMark = ? where contactId = ?";
						JsonArray params = new JsonArray();
						params.add(contactType);
						params.add(iter);
						params.add(rp.getString("itMark"));
						params.add(contactId);
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
				Supplier<Future<String>> lf = () -> {
					Future<String> f = Future.future(promise -> {
						String statusDesc = "";
						if (!Utils.strEquals(originIter, iter)) {
							statusDesc += "用户" + rp.getString("operator") + "修改了IT处理人员，从" + originIter + "改为" + iter
									+ "。";
						}
						if (!Utils.strEquals(originContactType, contactType)) {
							statusDesc += "用户" + rp.getString("operator") + "修改了工作联系单类别，从" + originContactType + "改为"
									+ contactType + "。";
						}
						if ("".equals(statusDesc)) {
							promise.complete();// 如果只是修改IT备注或未修改，那么不记录备注。
						} else {
							String sql = "insert into contact_log(logId,contactId,status,statusdesc," //
									+ "operator,operationdate) values(?,?,?,?,?,?)";
							JsonArray params = new JsonArray();
							params.add(UUID.randomUUID().toString());
							params.add(contactId);
							params.add("MODIFY");
							params.add(statusDesc);
							params.add(rp.getString("operator"));
							params.add(rp.getString("operationDate"));
							connection.updateWithParams(sql, params, r -> {
								if (r.succeeded()) {
									promise.complete();
								} else {
									promise.fail("fail to save update log.");
								}
							});
						}
					});
					return f;
				};
				// 3.EXCUTE
				uf.get().compose(r -> {
					return lf.get();
				}).setHandler(r -> {
					if (r.succeeded()) {
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
	 * 工作联系单删除
	 */
	public void handleDel(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		SQLClient client = ConfigVerticle.client;
		JsonObject rp = ctx.getBodyAsJson();
		int contactId = rp.getInteger("contactId");
		String delUser = rp.getString("delUser");
		String delDate = rp.getString("delDate");
		client.getConnection(cr -> {
			if (cr.succeeded()) {
				SQLConnection connection = cr.result();
				// 1.检查状态
				Supplier<Future<String>> checkf = () -> {
					Future<String> f = Future.future(promise -> {
						String sql = "select * from contact_log where contactId = ? and status = 'ACCEPTED'";
						JsonArray params = new JsonArray().add(contactId);
						connection.queryWithParams(sql, params, r -> {
							if (r.succeeded()) {
								if (r.result().getRows().size() > 0){
									promise.fail("工作联系单已被IT部受理,不能删除,请联系IT部取消");
								}else{
									promise.complete();
								}
							} else {
								promise.fail("fila to del contact.");
							}
						});
					});
					return f;
				};
				// 2.修改
				Supplier<Future<String>> updatef = () -> {
					Future<String> f = Future.future(promise -> {
						String sql = "update contact set indt = 'Y',delUser = ?,delDate = ? where contactId = ?";
						JsonArray params = new JsonArray();
						params.add(delUser);
						params.add(delDate);
						params.add(contactId);
						connection.updateWithParams(sql, params, r -> {
							if (r.succeeded()) {
								promise.complete();
							} else {
								promise.fail("fail to del contact.");
							}
						});
					});
					return f;
				};
				// 3.EXECUTE
				checkf.get().compose(r -> {
					return updatef.get();
				}).setHandler(r -> {
					if (r.succeeded()) {
						String message = "工作联系单" + contactId + "已删除";
						notifyHandler.sendMsg(contactId, message);
						res.end(OK());
					} else {
						res.end(Err(r.cause().getMessage()));
					}
				});
			} else {
				res.end(Err("fail to get db connection."));
			}
		});

	}

	/**
	 * 工作联系单受理
	 */
	public void handleAccept(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject rp = ctx.getBodyAsJson();
		SQLClient client = ConfigVerticle.client;
		client.getConnection(cr -> {
			if (cr.succeeded()) {
				SQLConnection connection = cr.result();
				// 1.受理
				Supplier<Future<String>> updatef = () -> {
					Future<String> f = Future.future(promise -> {
						String sql = "update contact set iter = ?,status = ? where contactId = ?";
						JsonArray params = new JsonArray();
						params.add(rp.getString("iter"));
						params.add("ACCEPTED");
						params.add(rp.getInteger("contactId"));
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
								+ "tag1,tagContent1,tag2,tagContent2,operator,operationdate) " //
								+ "values(?,?,?,?,?,?,?,?,?," + JdbcHelper.toDbDate(new Date()) + ")";
						JsonArray params = new JsonArray();
						params.add(UUID.randomUUID().toString());
						params.add(rp.getInteger("contactId"));
						params.add("ACCEPTED");
						params.add("用户" + rp.getString("operator") + "受理了IT工作联系单。目前由" + rp.getString("iter") + "负责处理。");
						params.add(rp.getString("tag1"));
						params.add(rp.getString("tagContent1"));
						params.add(rp.getString("tag2"));
						params.add(rp.getString("tagContent2"));
						params.add(rp.getString("operator"));
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
						String message = "联系单" + rp.getInteger("contactId") + "已受理";
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
	 * 工作联系单通用
	 */
	public void handleCommon(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject rp = ctx.getBodyAsJson();
		SQLClient client = ConfigVerticle.client;
		String newStatus = rp.getString("newStatus");
		client.getConnection(cr -> {
			if (cr.succeeded()) {
				SQLConnection connection = cr.result();
				// 1.受理
				Supplier<Future<String>> updatef = () -> {
					Future<String> f = Future.future(promise -> {
						String sql = "update contact set status = ? where contactId = ?";
						JsonArray params = new JsonArray();
						params.add(newStatus);
						params.add(rp.getInteger("contactId"));
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
								+ "tag1,tagContent1,tag2,tagContent2,operator,operationdate) " //
								+ "values(?,?,?,?,?,?,?,?,?," + JdbcHelper.toDbDate(new Date()) + ")";
						JsonArray params = new JsonArray();
						params.add(UUID.randomUUID().toString());
						params.add(rp.getInteger("contactId"));
						params.add(rp.getString("newStatus"));
						params.add("用户" + rp.getString("operator") + "将IT工作联系单状态置为" + newStatus);
						params.add(rp.getString("tag1"));
						params.add(rp.getString("tagContent1"));
						params.add(rp.getString("tag2"));
						params.add(rp.getString("tagContent2"));
						params.add(rp.getString("operator"));
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
						String message = "联系单" + rp.getInteger("contactId") + "状态改变为" + newStatus;
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
	 * 工作联系单退回
	 */
	public void handleGoback(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		JsonObject rp = ctx.getBodyAsJson();
		int contactId = rp.getInteger("contactId");
		SQLClient client = ConfigVerticle.client;
		client.getConnection(cr -> {
			if (cr.succeeded()) {
				SQLConnection connection = cr.result();
				// 1.退回
				Supplier<Future<String>> updatef = () -> {
					Future<String> f = Future.future(promise -> {
						String sql = "update contact set status = 'CHECKIN' where contactId = ? ";
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
								+ "tag1,tagcontent1,operator,operationdate) "//
								+ "values(?,?,?,?,?,?,?," + JdbcHelper.toDbDate(new Date()) + ")";
						JsonArray params = new JsonArray();
						params.add(UUID.randomUUID().toString());
						params.add(rp.getInteger("contactId"));
						params.add("GOBACK");
						params.add("用户" + rp.getString("operator") + "将工作联系单状态回退到登记状态。");
						params.add(rp.getString("tag1"));
						params.add(rp.getString("tagContent1"));
						params.add(rp.getString("operator"));
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
						String message = "联系单" + rp.getInteger("contactId") + "状态退回到CHECKIN";
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
	 * 工作联系单类别改变
	 */
	public void handleTypeChg(RoutingContext ctx) {
		JsonObject rp = ctx.getBodyAsJson();
		String sql = "update contact set contactType = ? where contactId = ? ";
		JsonArray params = new JsonArray();
		params.add(rp.getString("contactType"));
		params.add(rp.getInteger("contactId"));
		JdbcHelper.update(ctx, sql, params);
	}

	/**
	 * 回收站列表
	 */
	public void handleRecycle(RoutingContext ctx) {
		JsonObject rp = ctx.getBodyAsJson();
		String sql = "select * from contact where checkinUser = ? and"//
				+ " nvl(indt,' ') = 'Y' order by checkindate desc";
		JsonArray params = new JsonArray().add(rp.getString("checkinUser"));
		JdbcHelper.rows(ctx, sql, params);
	}

	/**
	 * 工作联系单时间线查询
	 */
	public void handleTimeline(RoutingContext ctx) {
		JsonObject rp = ctx.getBodyAsJson();
		String sql = "select a.*,b.contactType from contact_log a,contact b " + //
				" where a.contactId = ? and a.contactId = b.contactId order by a.operationdate";
		JsonArray params = new JsonArray().add(rp.getInteger("contactId"));
		JdbcHelper.rows(ctx, sql, params);
	}

	/**
	 * 首页-联系单动态
	 */
	public void contactLogList(RoutingContext ctx) {
		String sql = "select * from (select a.*,b.contactType from contact_log a,contact b " + //
				" where a.contactId = b.contactId order by operationdate desc) a " + //
				" where rownum <= 8";
		JdbcHelper.rows(ctx, sql);
	}

	/**
	 * 单个联系单
	 */
	public void singleContact(RoutingContext ctx) {
		JsonObject rp = ctx.getBodyAsJson();
		String sql = "select * from contact where contactId = ?";
		JsonArray params = new JsonArray().add(rp.getInteger("contactId"));
		JdbcHelper.oneRow(ctx, sql, params);
	}

}
