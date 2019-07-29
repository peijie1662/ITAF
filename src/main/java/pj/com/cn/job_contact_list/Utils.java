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

}
