package it.cloud.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.cloud.amazon.Configuration;

public abstract class ConfigurationFile {
	
	private static final Logger logger = LoggerFactory.getLogger(ConfigurationFile.class);

	public static ConfigurationFile parse() throws FileNotFoundException, IOException {
		return parse(Configuration.getPathToFile(Configuration.CONFIGURATION));
	}
	
	public static ConfigurationFile parse(Path p) throws FileNotFoundException, IOException {
		try {
			JSONFile file = new JSONFile(p);
			return file;
		} catch (Exception e) {
			PropertiesFile file = new PropertiesFile(p);
			return file;
		}
	}
	
	public abstract String getString(String key);
	public abstract String getString(String key, String defaultValue);
	public abstract int getInt(String key);
	public abstract int getInt(String key, int defaultValue);
	public abstract double getDouble(String key);
	public abstract double getDouble(String key, double defaultValue);
	public abstract String[] getStrings(String key);
	public abstract int[] getInts(String key);
	public abstract double[] getDoubles(String key);
	public abstract ConfigurationFile getElement(String key);
	public abstract String[] keys();
	public boolean hasElement(String key) {
		for (String tmp : keys()) {
			if (tmp.equals(key))
				return true;
		}
		return false;
	}
	
	public static class PropertiesFile extends ConfigurationFile {
		private Properties prop;
		private Path p;
		private String baseKey;
		
		private PropertiesFile(Path p, String baseKey) throws FileNotFoundException, IOException {
			this.p = p;
			if (baseKey == null)
				baseKey = "";
			this.baseKey = baseKey;
			
			prop = new Properties();
			prop.load(new FileInputStream(p.toFile()));
		}
		
		public PropertiesFile(Path p) throws FileNotFoundException, IOException {
			this(p, "");
		}
		
		public String getString(String key, String defaultValue) {
			return prop.getProperty(baseKey + key.toUpperCase(), defaultValue);
		}
		
		public String getString(String key) {
			return getString(key, null);
		}
		
		public int getInt(String key, int defaultValue) {
			return Integer.parseInt(getString(key, Integer.toString(defaultValue)));
		}
		
		public int getInt(String key) {
			return getInt(key, 0);
		}
		
		public double getDouble(String key, double defaultValue) {
			return Double.parseDouble(getString(key, Double.toString(defaultValue)));
		}
		
		public double getDouble(String key) {
			return getDouble(key, 0.0);
		}
		
		public String[] getStrings(String key) {
			String res = getString(key, null);
			if (res != null)
				return res.split(";");
			else
				return new String[0];
		}
		
		public int[] getInts(String key) {
			String[] strings = getStrings(key);
			if (strings != null && strings.length > 0) {
				int[] res = new int[strings.length];
				for (int i = 0; i < strings.length; ++i)
					res[i] = Integer.parseInt(strings[i]);
				return res;
			} else {
				return new int[0];
			}
		}
		
		public double[] getDoubles(String key) {
			String[] strings = getStrings(key);
			if (strings != null && strings.length > 0) {
				double[] res = new double[strings.length];
				for (int i = 0; i < strings.length; ++i)
					res[i] = Double.parseDouble(strings[i]);
				return res;
			} else {
				return new double[0];
			}
		}
		
		public String[] keys() {
			List<String> tmp = new ArrayList<String>();
			Enumeration<?> enu = prop.propertyNames();
			while (enu.hasMoreElements()) {
				String str = (String)enu.nextElement();
				if (baseKey != null && baseKey.length() > 0) {
					int j = str.indexOf(baseKey);
					if (j == -1)
						continue;
					str = str.substring(j + baseKey.length());
				}
				tmp.add(str);
			}
			
			boolean finished = false;
			while (!finished) {
				boolean goOn = true;
				for (int i = 0; i < tmp.size() && goOn; ++i) {
					String key = tmp.get(i);
					int count = 1;
					if (key.indexOf(PREFIX_SEPARATOR) == -1)
						continue;
					String probableActualKey = key.substring(0, key.indexOf(PREFIX_SEPARATOR));
					for (int j = i+1; j < tmp.size(); ++j) {
						String key2 = tmp.get(j);
						if (key2.indexOf(probableActualKey + PREFIX_SEPARATOR) > -1)
							count++;
					}
					if (count > MIN_COUNT) {
						goOn = false;
						for (int j = i; j < tmp.size(); ++j) {
							String key2 = tmp.get(j);
							if (key2.indexOf(probableActualKey + PREFIX_SEPARATOR) > -1) {
								tmp.remove(j);
								j--;
							}
						}
						tmp.add(probableActualKey);
					}
				}
				if (goOn)
					finished = true;
			}
			
			String[] res = new String[tmp.size()];
			for (int i = 0; i < res.length; ++i)
				res[i] = tmp.get(i);
			return res;
		}
		
		private boolean hasPrefix(String baseKey) {
			String[] keys = keys();
			for (String key : keys)
				if (key.indexOf(baseKey) > -1)
					return true;
			return false;
		}
		
		private static final String PREFIX_SEPARATOR = "_";
		private static final int MIN_COUNT = 3;
		
		public PropertiesFile getElement(String key) {
			try {
				if (!hasPrefix(key))
					return this;
				else
					return new PropertiesFile(p, baseKey + key + PREFIX_SEPARATOR);
			} catch (Exception e) {
				throw new RuntimeException(e.getMessage());
			}
		}
	}
	
	public static class JSONFile extends ConfigurationFile {
		private JSONObject obj;
		
		public JSONFile(Path p) throws IOException {
			this(new JSONObject(FileUtils.readFileToString(p.toFile())));
		}
		
		private JSONFile(JSONObject obj) {
			this.obj = obj;
		}
		
		public String getString(String key, String defaultValue) {
			return String.valueOf(get(key, defaultValue));
		}
		
		public String getString(String key) {
			return getString(key, null);
		}
		
		public int getInt(String key, int defaultValue) {
			return Integer.parseInt(getString(key, Integer.toString(defaultValue)));
		}
		
		public int getInt(String key) {
			return getInt(key, 0);
		}
		
		public double getDouble(String key, double defaultValue) {
			return Double.parseDouble(getString(key, Double.toString(defaultValue)));
		}
		
		public double getDouble(String key) {
			return getDouble(key, 0.0);
		}
		
		public String[] getStrings(String key) {
			Object[] array = getList(key);
			if (array != null && array.length > 0) {
				String[] res = new String[array.length];
				for (int i = 0; i < array.length; ++i)
					res[i] = array[i].toString();
				return res;
			} else {
				return new String[0];
			}
		}
		
		public int[] getInts(String key) {
			String[] array = getStrings(key);
			if (array != null && array.length > 0) {
				int[] res = new int[array.length];
				for (int i = 0; i < array.length; ++i)
					res[i] = Integer.parseInt(array[i]);
				return res;
			} else {
				return new int[0];
			}
		}
		
		public double[] getDoubles(String key) {
			String[] array = getStrings(key);
			if (array != null && array.length > 0) {
				double[] res = new double[array.length];
				for (int i = 0; i < array.length; ++i)
					res[i] = Double.parseDouble(array[i]);
				return res;
			} else {
				return new double[0];
			}
		}
		
		private Object[] getList(String key) {
			try {
				JSONArray array = obj.getJSONArray(key);
				Object[] res = new Object[array.length()];
				for (int i = 0; i < res.length; ++i)
					res[i] = array.get(i);
				return res;
			} catch (Exception e) {
				return new Object[0];
			}
		}
		
		private Object get(String key, Object defaultValue) {
			try {
				return obj.get(key);
			} catch (Exception e) {
				return defaultValue;
			}
		}
		
		@SuppressWarnings("unused")
		private Object get(String key) {
			return get(key, null);
		}
		
		public String[] keys() {
			return JSONObject.getNames(obj);
		}
		
		public JSONFile getElement(String key) {
			try {
				return new JSONFile(obj.getJSONObject(key));
			} catch (Exception e) {
				return this;
			}
		}
	}
	
	public static void main(String[] args) throws Exception {
		Path prop = Paths.get("/Users/ft/Development/workspace-s4c/modaclouds-tests/modaclouds-scalingsdatests/resources/configuration.properties");
		Path json = Paths.get("/Users/ft/Development/workspace-s4c/modaclouds-tests/modaclouds-scalingsdatests/resources/configuration.json");
		
		ConfigurationFile conf1 = ConfigurationFile.parse(json);
		ConfigurationFile conf2 = ConfigurationFile.parse(prop);
		
		StringBuffer sb =  new StringBuffer("HTTPAgent.KEYS1:");
		for (String s : conf1.getElement("machines").getElement("httpagent").keys()) {
			sb.append("\n> ");
			sb.append(s);
		}
		logger.debug(sb.toString());
		
		sb = new StringBuffer("HTTPAgent.KEYS2:");
		for (String s : conf2.getElement("machines").getElement("httpagent").keys()) {
			sb.append("\n> ");
			sb.append(s);
		}
		logger.debug(sb.toString());
		
		sb = new StringBuffer("ROOT.KEYS1:");
		for (String s : conf1.keys()) {
			sb.append("\n> ");
			sb.append(s);
		}
		logger.debug(sb.toString());
		
		sb = new StringBuffer("ROOT.KEYS2:");
		for (String s : conf2.keys()) {
			sb.append("\n> ");
			sb.append(s);
		}
		logger.debug(sb.toString());
		
		logger.debug("LB.AMI1: {}", conf2.getElement("machines").getElement("lb").getString("AMI"));
	}

}
