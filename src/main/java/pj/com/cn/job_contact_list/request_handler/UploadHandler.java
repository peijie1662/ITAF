package pj.com.cn.job_contact_list.request_handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.vertx.core.Vertx;
import io.vertx.core.file.CopyOptions;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import pj.com.cn.job_contact_list.ConfigVerticle;
import pj.com.cn.job_contact_list.model.CallResult;

/**
 * @author PJ
 * @version 创建时间：2019年6月21日 下午2:02:58 文件上传
 */
public class UploadHandler {

	private final static String CONTACT_UPLOAD_URL = ConfigVerticle.uploadDir;

	private final Vertx vertx;

	public UploadHandler(Vertx vertx) {
		this.vertx = vertx;
	}

	/**
	 * 联系单文件列表<br>
	 * 根据contactId返回当前联系单文件夹下文件列表
	 */
	public void contactFileList(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		CallResult<List<String>> result = new CallResult<List<String>>();
		try {
			FileSystem fs = vertx.fileSystem();
			JsonObject pp = ctx.getBodyAsJson();
			String userId = pp.getString("userId");
			int contactId = pp.getInteger("contactId") == null ? 0 : pp.getInteger("contactId");
			String dir = CONTACT_UPLOAD_URL + userId + "/" + contactId;
			List<String> fNames = new ArrayList<String>();
			if (fs.existsBlocking(dir)) {
				fNames = fs.readDirBlocking(dir).stream().map(f -> f.substring(f.lastIndexOf("\\") + 1))
						.collect(Collectors.toList());
			}
			result.ok(fNames);
		} catch (Exception e) {
			e.printStackTrace();
			result.err(e.getMessage());
		}
		res.end(result.toString());
	}

	/**
	 * 联系单附件上传 <br>
	 * 1.如果上传时没有contactId,存入用户文件夹 <br>
	 * 2.如果有contactId,存入用户文件夹下的联系单文件夹 <br>
	 */
	public void uploadContact(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		CallResult<String> result = new CallResult<String>();
		try {
			FileSystem fs = vertx.fileSystem();
			String userId = ctx.request().getFormAttribute("userId");
			String contactId = ctx.request().getFormAttribute("contactId");
			String userPath = contactId == null || "0".equals(contactId) ? CONTACT_UPLOAD_URL + userId
					: CONTACT_UPLOAD_URL + userId + "/" + contactId;
			if (!fs.existsBlocking(userPath)) {
				fs.mkdirsBlocking(userPath);
			}
			Set<FileUpload> uploads = ctx.fileUploads();
			result.ok();
			uploads.forEach(fileUpload -> {
				String path = userPath + "/" + fileUpload.fileName();
				fs.move(fileUpload.uploadedFileName(), path, new CopyOptions().setReplaceExisting(true), ar -> {
					if (!ar.succeeded()) {
						result.err(ar.cause().getMessage());
					}
				});
			});
			res.end(result.toString());
		} catch (Exception e) {
			e.printStackTrace();
			result.err(e.getMessage());
			res.end(result.toString());
		}
	}

	/**
	 * 文件归档<br>
	 * 通过指定文件列表和contactId,将文件列表从用户文件夹归档到联系单文件夹
	 */
	public void fileTheDocument(String userId, int contactId, String fileList) {
		if (!fileList.isEmpty()) {
			FileSystem fs = vertx.fileSystem();
			String userPath = CONTACT_UPLOAD_URL + userId;
			String contactPath = CONTACT_UPLOAD_URL + userId + "/" + contactId;
			if (!fs.existsBlocking(contactPath)) {
				fs.mkdirsBlocking(contactPath);
			}
			List<String> fNames = Arrays.asList(fileList.split(","));
			fNames.forEach(f -> {
				String ori_url = userPath + "/" + f;
				String tar_url = contactPath + "/" + f;
				if (fs.existsBlocking(ori_url)) {
					fs.moveBlocking(ori_url, tar_url);
				}
			});
		}
	}

	/**
	 * 文件下载<br>
	 * 注意:<br>
	 * CORS需要指定Header,否则并不能接收;<br>
	 * Header中的key不要大写(会被自动转小写)
	 */
	public void downloadContact(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		res.putHeader("Access-Control-Expose-Headers", "download_flag,err_msg");
		try {
			FileSystem fs = vertx.fileSystem();
			JsonObject pp = ctx.getBodyAsJson();
			String userId = pp.getString("userId");
			int contactId = pp.getInteger("contactId") == null ? 0 : pp.getInteger("contactId");
			String fileName = pp.getString("fileName");
			String url = contactId == 0 ? CONTACT_UPLOAD_URL + userId : CONTACT_UPLOAD_URL + userId + "/" + contactId;
			url += "/" + fileName;
			if (fs.existsBlocking(url)) {
				res.putHeader("download_flag", "true");
				res.sendFile(url);
			} else {
				res.putHeader("download_flag", "false");
				res.putHeader("err_msg", "download file is missing");
			}
		} catch (Exception e) {
			e.printStackTrace();
			res.putHeader("download_flag", "false");
			res.putHeader("err_msg", e.getMessage());
		}
		res.end();
	}

	/**
	 * 联系单附件删除<br>
	 */
	public void delContactFile(RoutingContext ctx) {
		HttpServerResponse res = ctx.response();
		res.putHeader("content-type", "application/json");
		CallResult<String> result = new CallResult<String>();
		try {
			FileSystem fs = vertx.fileSystem();
			JsonObject pp = ctx.getBodyAsJson();
			String userId = pp.getString("userId");
			int contactId = pp.getInteger("contactId");
			String fileName = pp.getString("fileName");
			String filePath = contactId == 0 ? CONTACT_UPLOAD_URL + userId
					: CONTACT_UPLOAD_URL + userId + "/" + contactId;
			filePath += "/" + fileName;
			fs.deleteBlocking(filePath);
			result.ok();
		} catch (Exception e) {
			e.printStackTrace();
			result.err(e.getMessage());
		}
		res.end(result.toString());
	}
}
