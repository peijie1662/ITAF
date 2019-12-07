package pj.com.cn.job_contact_list;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * @author PJ
 * @version 创建时间：2019年6月28日 下午9:53:45 类说明
 */
public final class Utils {

	public static String getUTCTimeStr() {
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		return df.format(new Date());
	}
	
	public static String getDBTimeStr() {
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		String ds = "to_date('"+ df.format(new Date()) +"','yyyy-mm-dd hh24:mi:ss')";
		return ds;
	}	
	
	public static boolean strEquals(String str1,String str2){
		if (str1 == null && str2 == null){
			return true;
		}else if(str1 != null){
			return str1.equals(str2);
		}
		return false;
	}

}
