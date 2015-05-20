package it.cloud;

import java.nio.file.Path;

public interface Instance {
	public String getIp();
	public String getSshUser();
	public String getSshPassword();
	public Path getKey();
	public String getName();
}
