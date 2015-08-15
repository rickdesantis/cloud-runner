package it.cloud;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class Configuration {
	
	public static final String CONFIGURATION = "configuration.json";
	public static final String FIREWALL_RULES = "firewallrules.csv";

	public static InputStream getInputStream(String filePath) {
		File f = new File(filePath);
		if (f.exists())
			try {
				return new FileInputStream(f);
			} catch (Exception e) { }
		
		InputStream is = Configuration.class.getResourceAsStream(filePath);
		if (is == null)
			is = Configuration.class.getResourceAsStream("/" + filePath);
		return is;
	}
	
	public static Path getPathToFile(String filePath) {
		File f = new File(filePath);
		if (f.exists())
			try {
				return f.toPath();
			} catch (Exception e) { }
		
		URL url = Configuration.class.getResource(filePath);
		if (url == null)
			url = Configuration.class.getResource("/" + filePath);
		if (url == null)
			return null;
		else
			return Paths.get(url.getPath());
	}

}
