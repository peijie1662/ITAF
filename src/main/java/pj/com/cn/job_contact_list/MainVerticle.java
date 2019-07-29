package pj.com.cn.job_contact_list;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.jdbc.JDBCClient;
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

	private JclConfig config;

	private JDBCClient dbClient;

	private JdbcHelper helper;

	@Override
	public void start(Future<Void> startFuture) throws Exception {
		config = new JclConfig(vertx);
		dbClient = config.getDbClient();
		helper = new JdbcHelper(this.dbClient);
		Router router = Router.router(vertx);
		router.route().handler(CorsHandler.create("*").allowedMethod(HttpMethod.GET).allowedMethod(HttpMethod.OPTIONS)
				.allowedMethod(HttpMethod.POST).allowedHeader("X-PINGARUNER").allowedHeader("Content-Type"));
		router.route().handler(BodyHandler.create());

		// 消息通知
		NotifyHandler notifyHandler = new NotifyHandler(dbClient);
		// 文件上传
		router.route().handler(BodyHandler.create());
		UploadHandler uploadHandler = new UploadHandler(vertx);
		// 通用联系单处理
		CommonHandler commonHandler = new CommonHandler(vertx, dbClient, helper);
		commonHandler.setNotifyHandler(notifyHandler);
		commonHandler.setUploadHandler(uploadHandler);
		// 数据联系单处理
		DataHandler dataHandler = new DataHandler(helper);
		dataHandler.setNotifyHandler(notifyHandler);
		// 开发联系单处理
		DevHandler devHandler = new DevHandler(dbClient);
		devHandler.setNotifyHandler(notifyHandler);
		// 改进项目处理
		ImproveHandler improveHandler = new ImproveHandler(dbClient);
		improveHandler.setNotifyHandler(notifyHandler);
		// 登录
		router.post("/login").handler(commonHandler::handleLogin);
		// 单个联系单
		router.post("/single").handler(commonHandler::singleContact);
		// 修改工作联系单列表
		router.post("/list/data").handler(dataHandler::handleDataContacts);
		// 开发工作联系单列表
		router.post("/list/dev").handler(devHandler::handleDevContacts);
		// 改进项目列表
		router.post("/list/improve").handler(improveHandler::handleImproveContacts);
		// 财务审核列表
		router.post("/list/fincheck").handler(dataHandler::handleFinCheckContacts);
		// 回收站列表
		router.post("/list/recycle").handler(commonHandler::handleRecycle);
		// 用户消息列表
		router.post("/list/notify").handler(notifyHandler::handleNotify);
		// 联系单保存修改(CHECKIN/MODIFY)
		router.post("/contact/save").handler(commonHandler::handleSave);
		// 联系单处理人员及IT备注
		router.post("/contact/iterandmark").handler(commonHandler::handleIterAndMark);
		// 联系单删除
		router.post("/contact/del").handler(commonHandler::handleDel);
		// 联系单受理(ACCEPTED)
		router.post("/contact/accept").handler(commonHandler::handleAccept);
		// 联系单回退
		router.post("/contact/goback").handler(commonHandler::handleGoback);
		// 联系单通用(SUSPEND/CANCEL)
		router.post("/contact/common").handler(commonHandler::handleCommon);
		// 联系单时间线查询
		router.post("/contact/timeline").handler(commonHandler::handleTimeline);
		// 财务审核
		router.post("/contact/fincheck").handler(dataHandler::handleFinCheck);
		// 联系单附件上传
		router.post("/contact/upload").handler(uploadHandler::uploadContact);
		// 联系单附件下载
		router.post("/contact/download").handler(uploadHandler::downloadContact);
		// 联系单附件删除
		router.post("/contact/delfile").handler(uploadHandler::delContactFile);
		// 联系单附件列表
		router.post("/contact/listfile").handler(uploadHandler::contactFileList);
		// 单个服务
		router.post("/readmsg").handler(notifyHandler::readMsg);
		// 首页-上线列表
		router.post("/list/online").handler(devHandler::onlineList);
		// 首页-联系单动态
		router.post("/list/contactlog").handler(commonHandler::contactLogList);
		// 公告-保存
		router.post("/broadcast/save").handler(notifyHandler::saveBroadcast);
		// 公告-列表
		router.post("/broadcast/list").handler(notifyHandler::broadcastList);
		// 启动服务
		vertx.createHttpServer().requestHandler(router).listen(config.getItafPort());
	}

}
