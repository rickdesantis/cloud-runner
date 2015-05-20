package it.cloud;

public interface VirtualMachine {
	public int getInstancesNeeded();
	public int getInstancesRunning();
	public String getSize();
	public String getImageId();
	public void addRunningInstance(String id, String contractId);
}
