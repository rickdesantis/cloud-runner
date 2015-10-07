package it.cloud.flexiant;

import java.util.Map;

import it.cloud.utils.CloudException;

public class VirtualMachine extends it.cloud.VirtualMachine {

	public VirtualMachine(String name, String ami, int instances, String size, int diskSize, double maxPrice, String os,
			String keyName, String sshUser, String sshPassword, Map<String, String> otherParams) throws CloudException {
		super(name, ami, instances, size, diskSize, maxPrice, os, keyName, sshUser, sshPassword, otherParams);
	}

	@Override
	public void addRunningInstance(String id, String contractId) {
		throw new RuntimeException("Method not implemented.");
	}

	@Override
	public String getIp(String id) {
		throw new RuntimeException("Method not implemented.");
	}

}
