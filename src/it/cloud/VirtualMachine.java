package it.cloud;

import java.util.List;

public interface VirtualMachine {
	public int getInstancesNeeded();
	public int getInstancesRunning();
	public String getSize();
	public String getImageId();
	public void addRunningInstance(String id, String contractId);
	public List<? extends Instance> getInstances();
	public void retrieveFiles(String[] filesToBeGet, String localPath, String remotePath) throws Exception;
	public void retrieveFiles(String localPath, String remotePath) throws Exception;
	public void deleteFiles() throws Exception;
	public void deleteFiles(String[] filesToBeDeleted) throws Exception;
}
