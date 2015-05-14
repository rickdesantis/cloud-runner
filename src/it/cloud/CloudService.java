package it.cloud;

import it.cloud.amazon.ec2.VirtualMachine;

import java.util.List;

public interface CloudService {
	public List<Instance> getRunningMachinesByImageId(String imageId);
	public List<Instance> startMachines(int n, String imageId);
	
	public static Class<? extends CloudService> getByName(String name) {
		switch (name) {
		case "amazon":
		case "ec2":
		case "aws-ec2":
			return VirtualMachine.class;
		}
		return null;
	}
}
