package it.cloud.utils;

import java.util.concurrent.TimeUnit;

public abstract class Utilities {
	
	public static String durationToString(long duration) {
		StringBuilder sb = new StringBuilder();
		{
			int res = (int) TimeUnit.MILLISECONDS.toSeconds(duration);
			if (res > 60 * 60) {
				sb.append(res / (60 * 60));
				sb.append(" h ");
				res = res % (60 * 60);
			}
			if (res > 60) {
				sb.append(res / 60);
				sb.append(" m ");
				res = res % 60;
			}
			sb.append(res);
			sb.append(" s");
		}

		return sb.toString();
	}

}
