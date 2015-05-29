package it.cloud;

import it.cloud.amazon.ec2.AmazonEC2;

import java.util.List;

public interface CloudService {
	public List<Instance> getRunningMachinesByImageId(String imageId);
	public List<Instance> startMachines(int n, String imageId);
	public void addRunningInstances(VirtualMachine vm);
	public String getVMNameByImageId(String imageId);
}
