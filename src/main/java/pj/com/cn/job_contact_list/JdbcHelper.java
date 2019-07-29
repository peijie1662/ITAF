package pj.com.cn.job_contact_list;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;

/**
 * @author PJ
 * @version 创建时间：2019年4月10日 下午12:37:19 类说明
 */
public class JdbcHelper {

	private JDBCClient client;

	public JdbcHelper(JDBCClient client) {
		this.client = client;
	}

	/**
	 * 记录集
	 * 
	 * @param sql
	 * @param args
	 * @param fun
	 */
	public void getRows(String sql, JsonArray args, Consumer<JsonArray> successFun, Consumer<JsonObject> failFun) {
		client.queryWithParams(sql, args, result -> {
			if (result.succeeded()) {
				ResultSet resultSet = result.result();
				JsonArray arr = new JsonArray(resultSet.getRows());
				successFun.accept(arr);
			} else {
				JsonObject fail = new JsonObject();
				fail.put("errMsg", result.cause().getMessage());
				failFun.accept(fail);
			}
		});
	}

	/**
	 * 第一行数据 ，为空则异常 多用于单行数据获得
	 * 
	 * @param sql
	 * @param args
	 * @param fun
	 */
	public void getOneRow(String sql, JsonArray args, Consumer<JsonObject> successFun, Consumer<JsonObject> failFun) {
		client.queryWithParams(sql, args, result -> {
			if (result.succeeded()) {
				JsonObject j = result.result().getRows().get(0);
				successFun.accept(j);
			} else {
				JsonObject fail = new JsonObject();
				fail.put("errMsg", result.cause().getMessage());
				failFun.accept(fail);
			}
		});
	}

	/**
	 * 判断SQL数据是否存在 ，不存在则异常
	 * 
	 * @param sql
	 * @param args
	 * @param fun
	 */
	public void isExpect(String sql, JsonArray args, Consumer<JsonObject> successFun, Consumer<JsonObject> failFun,
			String errMsg) {
		client.queryWithParams(sql, args, result -> {
			if (result.succeeded()) {
				if (result.result().getNumRows() > 0) {
					JsonObject j = result.result().getRows().get(0);
					successFun.accept(j);
				} else {
					JsonObject fail = new JsonObject();
					fail.put("errMsg", errMsg);
					failFun.accept(fail);
				}
			} else {
				JsonObject fail = new JsonObject();
				fail.put("errMsg", result.cause().getMessage());
				failFun.accept(fail);
			}
		});
	}

	/**
	 * 判断SQL数据是否不存在 ，存在则异常
	 * 
	 * @param sql
	 * @param args
	 * @param fun
	 */
	public void notExpect(String sql, JsonArray args, Consumer<JsonObject> successFun, Consumer<JsonObject> failFun,
			String errMsg) {
		client.queryWithParams(sql, args, result -> {
			if (result.succeeded()) {
				if (result.result().getNumRows() == 0) {
					successFun.accept(new JsonObject());
				} else {
					JsonObject fail = new JsonObject();
					fail.put("errMsg", errMsg);
					failFun.accept(fail);
				}
			} else {
				JsonObject fail = new JsonObject();
				fail.put("errMsg", result.cause().getMessage());
				failFun.accept(fail);
			}
		});
	}

	/**
	 * 执行
	 * 
	 * @param sql
	 * @param args
	 * @param fun
	 */
	public void exeSql(String sql, JsonArray args, Consumer<Integer> successFun, Consumer<JsonObject> failFun) {	
		client.updateWithParams(sql, args, result -> {
			if (result.succeeded()) {
				int r = result.result().getUpdated();
				successFun.accept(r);
			} else {				
				JsonObject fail = new JsonObject();
				fail.put("errMsg", result.cause().getMessage());
				failFun.accept(fail);
			}
		});
	}

	/**
	 * 转换日期字符串
	 */
	public String toDbDate(Date dt) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String dtStr = "to_date('" + sdf.format(dt) + "','yyyy-mm-dd hh24:mi:ss')";
		return dtStr;
	}

	/**
	 * 批量执行SQL
	 */
	public List<Integer> batchSql(List<String> sqls) {
		return null;
	}

	public JDBCClient getClient() {
		return client;
	}

	public void setClient(JDBCClient client) {
		this.client = client;
	}

}
