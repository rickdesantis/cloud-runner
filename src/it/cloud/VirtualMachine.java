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
	public String getParameter(String name);
	
	public static final String MACHINES_KEY = "machines";
	public static final String IMAGE_ID_KEY = "AMI";
	public static final String SIZE_KEY = "size";
	public static final String DONT_OVERRIDE_TYPE_KEY = "dont_override_type";
	public static final String INSTANCES_KEY = "instances";
	public static final String DISC_KEY = "disk";
	public static final String OS_KEY = "OS";
	public static final String KEYPAIR_NAME_KEY = "keypair_name";
	public static final String SSH_USER_KEY = "SSH_user";
	public static final String SSH_PASS_KEY = "SSH_pass";
	public static final String PROVIDER_KEY = "provider";
}
