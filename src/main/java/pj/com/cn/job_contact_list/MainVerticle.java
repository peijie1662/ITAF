package pj.com.cn.job_contact_list;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import pj.com.cn.job_contact_list.request_handler.CommonHandler;
import pj.com.cn.job_contact_list.request_handler.DataHandler;
import pj.com.cn.job_contact_list.request_handler.DevHandler;
import pj.com.cn.job_contact_list.request_handler.ImproveHandler;
import pj.com.cn.job_contact_list.request_handler.NotifyHandler;
import pj.com.cn.job_contact_list.request_handler.UploadHandler;

/**
 * @author PJ
 * @version 创建时间：2019年4月10日 上午9:58:29 入口
 */
public class MainVerticle extends AbstractVerticle {

	@Override
	public void start(Future<Void> startFuture) throws Exception {
		Router router = Router.router(vertx);
		router.route()
				.handler(CorsHandler.create("*")//
						.allowedMethod(HttpMethod.GET)//
						.allowedMethod(HttpMethod.OPTIONS)//
						.allowedMethod(HttpMethod.POST)//
						.allowedHeader("X-PINGARUNER")//
						.allowedHeader("Content-Type"));
		router.route().handler(BodyHandler.create());
		// 消息通知
		NotifyHandler notifyHandler = new NotifyHandler();
		// 文件上传
		router.route().handler(BodyHandler.create());
		UploadHandler uploadHandler = new UploadHandler(vertx);
		// 通用联系单处理
		CommonHandler commonHandler = new CommonHandler().setNotifyHandler(notifyHandler)
				.setUploadHandler(uploadHandler);
		// 数据联系单处理
		DataHandler dataHandler = new DataHandler().setNotifyHandler(notifyHandler);
		// 开发联系单处理
		DevHandler devHandler = new DevHandler().setNotifyHandler(notifyHandler);
		// 改进项目处理
		ImproveHandler improveHandler = new ImproveHandler();
		// 登录
		router.post("/login").blockingHandler(commonHandler::handleLogin, false);
		// 单个联系单
		router.post("/single").blockingHandler(commonHandler::singleContact, false);
		// 修改工作联系单列表
		router.post("/list/data").blockingHandler(dataHandler::handleDataContacts, false);
		// 开发工作联系单列表
		router.post("/list/dev").blockingHandler(devHandler::handleDevContacts, false);
		// 改进项目列表
		router.post("/list/improve").blockingHandler(improveHandler::handleImproveContacts, false);
		// 财务审核列表
		router.post("/list/fincheck").blockingHandler(dataHandler::handleFinCheckContacts, false);
		// 财务相关列表
		router.post("/list/finlink").blockingHandler(devHandler::handleFinLinkContacts, false);
		// 回收站列表
		router.post("/list/recycle").blockingHandler(commonHandler::handleRecycle, false);
		// 用户消息列表
		router.post("/list/notify").blockingHandler(notifyHandler::handleNotify, false);
		// 联系单修改类别
		router.post("/contact/typechg").blockingHandler(commonHandler::handleTypeChg, false);
		// 联系单保存修改(CHECKIN/MODIFY)
		router.post("/contact/save").blockingHandler(commonHandler::handleSave, false);
		// 联系单处理人员及IT备注
		router.post("/contact/iterandmark").blockingHandler(commonHandler::handleIterAndMark, false);
		// 联系单删除
		router.post("/contact/del").blockingHandler(commonHandler::handleDel, false);
		// 联系单受理(ACCEPTED)
		router.post("/contact/accept").blockingHandler(commonHandler::handleAccept, false);
		// 联系单回退
		router.post("/contact/goback").blockingHandler(commonHandler::handleGoback, false);
		// 联系单通用(SUSPEND/CANCEL)
		router.post("/contact/common").blockingHandler(commonHandler::handleCommon, false);
		// 联系单时间线查询
		router.post("/contact/timeline").blockingHandler(commonHandler::handleTimeline, false);
		// 财务审核
		router.post("/contact/fincheck").blockingHandler(dataHandler::handleFinCheck, false);
		// 财务相关
		router.post("/contact/finlink").blockingHandler(devHandler::handleFinLink, false);
		// 联系单附件上传
		router.post("/contact/upload").blockingHandler(uploadHandler::uploadContact, false);
		// 联系单附件下载
		router.post("/contact/download").blockingHandler(uploadHandler::downloadContact, false);
		// 联系单附件删除
		router.post("/contact/delfile").blockingHandler(uploadHandler::delContactFile, false);
		// 联系单附件列表
		router.post("/contact/listfile").blockingHandler(uploadHandler::contactFileList, false);
		// 单个服务
		router.post("/readmsg").blockingHandler(notifyHandler::readMsg, false);
		// 首页-上线列表
		router.post("/list/online").blockingHandler(devHandler::onlineList, false);
		// 首页-联系单动态
		router.post("/list/contactlog").blockingHandler(commonHandler::contactLogList, false);
		// 公告-保存
		router.post("/broadcast/save").blockingHandler(notifyHandler::saveBroadcast, false);
		// 公告-列表
		router.post("/broadcast/list").blockingHandler(notifyHandler::broadcastList, false);
		// 启动服务
		ConfigVerticle config = new ConfigVerticle(vertx);
		vertx.deployVerticle(new TaskVerticle());
		vertx.createHttpServer().requestHandler(router).listen(config.getItafPort());
	}

}
