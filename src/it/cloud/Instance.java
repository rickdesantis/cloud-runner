package it.cloud;

import java.nio.file.Path;
import java.util.List;

public interface Instance {
	public String getIp();
	public String getSshUser();
	public String getSshPassword();
	public Path getKey();
	public String getName();
	
	public List<String> exec(String cmd) throws Exception;
	public List<String> execStarter() throws Exception;
	public List<String> execStopper() throws Exception;
	public List<String> execUpdater() throws Exception;
	public List<String> execDownloader() throws Exception;
	public List<String> execInstaller() throws Exception;
	public void terminate();
	public void reboot();
}
