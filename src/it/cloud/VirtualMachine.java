package it.cloud;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.cloud.utils.CloudException;
import it.cloud.utils.Ssh;

public abstract class VirtualMachine {
	
	protected static final Logger logger = LoggerFactory.getLogger(VirtualMachine.class);

	protected String ami;
	protected int instances;
	protected String size;
	protected int diskSize;
	protected double maxPrice;
	protected String name;
	protected String userData;
	protected String os;
	protected String keyName;
	protected List<Instance> instancesSet;
	
	protected Map<String, String> otherParams;

	protected String sshUser;
	protected String sshPassword;

	public int getInstancesNeeded() {
		return instances;
	}

	public int getInstancesRunning() {
		return instancesSet.size();
	}
	
	public List<Instance> getInstances() {
		return instancesSet;
	}
	
	public List<String> getIps() {
		List<String> res = new ArrayList<String>();
		for (Instance i : instancesSet) {
			String ip = i.getIp();
			if (ip != null)
				res.add(ip);
		}
		return res;
	}

	public String getSize() {
		return size;
	}

	public String getImageId() {
		return ami;
	}
	
	public String toString() {
		return String.format(
				"VM: %s [%s], %d instance%s of size %s, %d GB of disk", name,
				ami, instances, instances == 1 ? "" : "s", size, diskSize);
	}

	public String getParameter(String name) {
		return otherParams.get(name);
	}
	
	public VirtualMachine(String name, String ami, int instances, String size,
			int diskSize, double maxPrice, String os, String keyName,
			String sshUser, String sshPassword, Map<String, String> otherParams)
			throws CloudException {
		if (instances <= 0 || diskSize <= 0)
			throw new CloudException(
					"There's some error in your configuration, aborting.");

		this.ami = ami;
		this.instances = instances;
		this.size = size;
		this.maxPrice = maxPrice;
		this.name = name;
		this.diskSize = diskSize;
		this.os = os;
		this.keyName = keyName;
		this.sshUser = sshUser;
		this.sshPassword = sshPassword;

		this.otherParams = otherParams;

		instancesSet = new ArrayList<Instance>();

		logger.debug(toString());
	}
	
	public boolean waitUntilRunning() {
		return waitUntilRunning(false);
	}

	public boolean waitUntilRunning(boolean initializedIsEnough) {
		if (instancesSet.size() == 0) {
			logger.error("You didn't start any machine!");
			return false;
		}

		for (Instance i : instancesSet) {
			boolean res = i.waitUntilRunning(initializedIsEnough);
			if (res == false)
				return false;
		}

		return true;
	}
	
	public abstract void addRunningInstance(String id, String contractId);
	
	public void retrieveFiles(String localPath, String remotePath) throws Exception {
		String filesToBeGet = getParameter("RETRIEVE_FILES");
		if (filesToBeGet != null)
			retrieveFiles(filesToBeGet.split(";"), localPath, remotePath);
	}

	public void retrieveFiles(String[] filesToBeGet, String localPath, String remotePath) throws Exception {
		int count = 1;
		if (filesToBeGet != null && filesToBeGet.length > 0)
			for (Instance i : instancesSet) {
				for (String s : filesToBeGet) {
					String actualRemotePath;
					if (s.startsWith("/"))
						actualRemotePath = s;
					else
						actualRemotePath = Paths.get(remotePath, s).toString();

					String fileName = s;
					if (s.startsWith("/"))
						fileName = s.substring(1);

					Paths.get(localPath, i.getName() + count, fileName).toFile().getParentFile().mkdirs();

					i.receiveFile(Paths.get(localPath, i.getName() + count, fileName).toString(), actualRemotePath);
				}

				++count;
			}
	}
	
	public static void retrieveFiles(List<String> ids, VirtualMachine vm, String localPath, String remotePath) throws Exception {
		String filesToBeGet = vm.getParameter("RETRIEVE_FILES");
		if (filesToBeGet != null)
			retrieveFiles(ids, vm, filesToBeGet.split(";"), localPath, remotePath);
	}
	
	public static void retrieveFiles(List<String> ids, VirtualMachine vm, String[] filesToBeGet, String localPath, String remotePath) throws Exception {
		if (filesToBeGet != null && filesToBeGet.length > 0) {
			int count = 1;
			
			for (String id : ids) {
				String ip = vm.getIp(id);
				
				for (String s : filesToBeGet) {
					String actualRemotePath;
					if (s.startsWith("/"))
						actualRemotePath = s;
					else
						actualRemotePath = Paths.get(remotePath, s).toString();
	
					String fileName = s;
					if (s.startsWith("/"))
						fileName = s.substring(1);
	
					Paths.get(localPath, vm.name + count, fileName).toFile().getParentFile().mkdirs();
	
					Ssh.receiveFile(ip, vm, Paths.get(localPath, vm.name + count, fileName).toString(), actualRemotePath);
				}
				
				++count;
			}
		}
	}
	
	public static void retrieveFiles(String ip, VirtualMachine vm, int count, String localPath, String remotePath) throws Exception {
		String filesToBeGet = vm.getParameter("RETRIEVE_FILES");
		if (filesToBeGet != null)
			retrieveFiles(ip, vm, count, filesToBeGet.split(";"), localPath, remotePath);
	}
	
	public static void retrieveFiles(String ip, VirtualMachine vm, int count, String[] filesToBeGet, String localPath, String remotePath) throws Exception {
		if (filesToBeGet != null && filesToBeGet.length > 0)
			for (String s : filesToBeGet) {
				String actualRemotePath;
				if (s.startsWith("/"))
					actualRemotePath = s;
				else
					actualRemotePath = Paths.get(remotePath, s).toString();

				String fileName = s;
				if (s.startsWith("/"))
					fileName = s.substring(1);

				Paths.get(localPath, vm.name + count, fileName).toFile().getParentFile().mkdirs();

				Ssh.receiveFile(ip, vm, Paths.get(localPath, vm.name + count, fileName).toString(), actualRemotePath);
			}
	}
	
	public void deleteFiles() throws Exception {
		String filesToBeDeleted = getParameter("DELETE_FILES");
		if (filesToBeDeleted != null)
			deleteFiles(filesToBeDeleted.split(";"));
	}

	public void deleteFiles(String[] filesToBeDeleted) throws Exception {
		if (filesToBeDeleted != null && filesToBeDeleted.length > 0)
			for (String s : filesToBeDeleted) {
				for (Instance i : instancesSet) {
					i.exec(String.format(
							"rm -rf %s",
							s));
				}
			}
	}
	
	public static void deleteFiles(String ip, VirtualMachine vm) throws Exception {
		String filesToBeDeleted = vm.getParameter("DELETE_FILES");
		if (filesToBeDeleted != null)
			deleteFiles(ip, vm, filesToBeDeleted.split(";"));
	}

	public static void deleteFiles(String ip, VirtualMachine vm, String[] filesToBeDeleted) throws Exception {
		if (filesToBeDeleted != null && filesToBeDeleted.length > 0)
			for (String s : filesToBeDeleted) {
				Ssh.exec(ip, vm, String.format(
						"rm -rf %s",
						s));
			}
	}
	
	public List<String> execStarter() throws Exception {
		String cmd = getParameter("STARTER");
		if (cmd != null)
			return exec(cmd);
		return new ArrayList<String>();
	}
	public List<String> execStopper() throws Exception {
		String cmd = getParameter("STOPPER");
		if (cmd != null)
			return exec(cmd);
		return new ArrayList<String>();
	}
	public List<String> execUpdater() throws Exception {
		String cmd = getParameter("UPDATER");
		if (cmd != null)
			return exec(cmd);
		return new ArrayList<String>();
	}
	public List<String> execDownloader() throws Exception {
		String cmd = getParameter("DOWNLOADER");
		if (cmd != null)
			return exec(cmd);
		return new ArrayList<String>();
	}
	public List<String> execInstaller() throws Exception {
		String cmd = getParameter("INSTALLER");
		if (cmd != null)
			return exec(cmd);
		return new ArrayList<String>();
	}
	
	public List<String> exec(String cmd) throws Exception {
		List<String> res = new ArrayList<String>();
		for (Instance i : getInstances())
			res.addAll(i.exec(cmd));
		return res;
	}
	
	public void terminate() {
		if (instancesSet.size() == 0)
			return;

		for (Instance i : instancesSet)
			i.terminate();

		instancesSet.clear();
	}

	public void reboot() {
		if (instancesSet.size() == 0)
			return;

		for (Instance i : instancesSet)
			i.reboot();
	}
	
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

	public abstract String getIp(String id);
}
