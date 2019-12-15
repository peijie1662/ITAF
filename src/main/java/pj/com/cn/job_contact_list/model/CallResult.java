package pj.com.cn.job_contact_list.model;

import io.vertx.core.json.JsonObject;

/**
 * @author PJ
 * @version 创建时间：2019年12月13日 上午10:12:26
 */
public class CallResult<T> {

	private boolean flag;

	private T data;

	private String errMsg;
	
	public CallResult() {

	}

	public CallResult(boolean flag, T data, String errMsg) {
		this.flag = flag;
		this.data = data;
		this.errMsg = errMsg;
	}

	public boolean isFlag() {
		return flag;
	}

	public static <T> CallResult<T> OK() {
		return new CallResult<T>(true, null, "");
	}

	public static <T> CallResult<T> OK(T data) {
		return new CallResult<T>(true, data, "");
	}

	public static <T> CallResult<T> Err() {
		return new CallResult<T>(false, null, "");
	}

	public static <T> CallResult<T> Err(String errMsg) {
		return new CallResult<T>(false, null, errMsg);
	}

	public CallResult<T> ok() {
		this.flag = true;
		return this;
	}

	public CallResult<T> ok(T data) {
		this.flag = true;
		this.data = data;
		return this;
	}

	public CallResult<T> err() {
		this.flag = false;
		return this;
	}

	public CallResult<T> err(String errMsg) {
		this.flag = false;
		this.errMsg = errMsg;
		return this;
	}

	@Override
	public String toString() {
		JsonObject j = new JsonObject()//
				.put("flag", this.flag)//
				.put("data", this.data)//
				.put("errMsg", this.errMsg);
		return j.encodePrettily();
	}

}
