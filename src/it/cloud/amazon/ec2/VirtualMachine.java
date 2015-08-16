package it.cloud.amazon.ec2;

import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.amazonaws.services.cloudwatch.model.Statistic;
import com.amazonaws.services.ec2.model.BlockDeviceMapping;
import com.amazonaws.services.ec2.model.CancelSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsResult;
import com.amazonaws.services.ec2.model.EbsBlockDevice;
import com.amazonaws.services.ec2.model.LaunchSpecification;
import com.amazonaws.services.ec2.model.RebootInstancesRequest;
import com.amazonaws.services.ec2.model.RequestSpotInstancesRequest;
import com.amazonaws.services.ec2.model.RequestSpotInstancesResult;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.SpotInstanceRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.util.Base64;

import it.cloud.amazon.Configuration;
import it.cloud.amazon.cloudwatch.CloudWatch;
import it.cloud.utils.CloudException;
import it.cloud.utils.ConfigurationFile;
import it.cloud.utils.Ssh;

public class VirtualMachine implements it.cloud.VirtualMachine {

	private static final Logger logger = LoggerFactory.getLogger(VirtualMachine.class);

	private String ami;
	private int instances;
	private String size;
	private int diskSize;
	private double maxPrice;
	private String name;
	private String userData;
	@SuppressWarnings("unused")
	private String os;
	private String keyName;
	private List<Instance> instancesSet;

	@Override
	public int getInstancesNeeded() {
		return instances;
	}

	@Override
	public int getInstancesRunning() {
		return instancesSet.size();
	}

	@Override
	public String getSize() {
		return size;
	}

	@Override
	public String getImageId() {
		return ami;
	}

	private Map<String, String> otherParams;

	private String sshUser;
	private String sshPassword;

	public List<Instance> getInstances() {
		return instancesSet;
	}

	public String toString() {
		return String.format(
				"VM: %s [%s], %d instance%s of size %s, %d GB of disk", name,
				ami, instances, instances == 1 ? "" : "s", size, diskSize);
	}

	public String getParameter(String name) {
		return otherParams.get(name);
	}

	public static VirtualMachine getVM(String name) throws CloudException {
		return getVM(name, null, -1);
	}

	public static VirtualMachine getVM(String name, String overrideSize) throws CloudException {
		return getVM(name, overrideSize, -1);
	}

	public static VirtualMachine getVM(String name, String overrideSize, int overrideInstances) throws CloudException {
		try {
			ConfigurationFile conf = ConfigurationFile.parse().getElement(MACHINES_KEY);
			if (!conf.hasElement(name))
				throw new CloudException("VM not found in the configuration file!");
			
			conf = conf.getElement(name);
			
			String provider = conf.getString(PROVIDER_KEY);
			if (provider != null && !provider.equals("Amazon"))
				logger.warn("You're considering this VM as if it was on Amazon, but it is on {} instead!", provider);

			String ami = conf.getString(IMAGE_ID_KEY);
			String size = conf.getString(SIZE_KEY);
			boolean notOverrideSize = Boolean.parseBoolean(conf.getString(DONT_OVERRIDE_TYPE_KEY));
			if (overrideSize != null && !notOverrideSize)
				size = overrideSize;
			int instances = conf.getInt(INSTANCES_KEY);
			if (overrideInstances > 0)
				instances = overrideInstances;
			int diskSize = conf.getInt(DISC_KEY);
			String os = conf.getString(OS_KEY);
			String keyName = conf.getString(KEYPAIR_NAME_KEY);
			String sshUser = conf.getString(SSH_USER_KEY);
			String sshPassword = conf.getString(SSH_PASS_KEY);

			Map<String, String> otherParams = new HashMap<String, String>();

			for (String key : conf.keys()) {
				otherParams.put(key.toUpperCase(), conf.getString(key));
			}

			if (ami != null && size != null && instances != 0
					&& diskSize != 0 && os != null && keyName != null
					&& sshUser != null && sshPassword != null) {
				double[] pricesInRegion = AmazonEC2.getPricesInRegion(size, os);
				if (pricesInRegion.length == 0)
					return null;
				double maxPrice = pricesInRegion[0];
				for (int i = 1; i < pricesInRegion.length; ++i)
					if (pricesInRegion[i] > maxPrice)
						maxPrice = pricesInRegion[i];

				maxPrice += Configuration.PRICE_MARGIN;

				return new VirtualMachine(name, ami,
						instances, size,
						diskSize, maxPrice, os, keyName,
						sshUser, sshPassword, otherParams);
			}
			throw new CloudException("The configuration for the VM isn't correct.");
		} catch (Exception e) {
			throw new CloudException("Error while loading the configuration.",
					e);
		}
	}

	public VirtualMachine(String name, String ami, int instances, String size,
			int diskSize, double maxPrice, String os, String keyName,
			String sshUser, String sshPassword, Map<String, String> otherParams)
			throws CloudException {
		if (Configuration.AWS_CREDENTIALS == null)
			throw new CloudException(
					"You didn't provide a valid credentials file, aborting.");

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

		instancesSet = new ArrayList<VirtualMachine.Instance>();

		logger.debug(toString());

		String file = "configuration-" + name + ".txt";

		InputStream is = Configuration.getInputStream(file);

		if (is != null)
			try (Scanner sc = new Scanner(is);) {
				userData = "";
				while (sc.hasNextLine())
					userData += sc.nextLine() + "\n";
				userData = String.format(userData.trim(),
						Configuration.AWS_CREDENTIALS.getAWSAccessKeyId(),
						Configuration.AWS_CREDENTIALS.getAWSSecretKey(),
						Configuration.REGION);
				logger.debug("Configuration for " + name + ":\n" + userData);

			}

	}

	public void spotRequest() {
		if (instances <= instancesSet.size()) {
			logger.info("No more instances will be launched because there are already enough.");
			return;
		}

		com.amazonaws.services.ec2.AmazonEC2 client = AmazonEC2.connect();

		RequestSpotInstancesRequest requestRequest = new RequestSpotInstancesRequest();

		requestRequest.setSpotPrice(Double.valueOf(maxPrice).toString());
//		requestRequest.setInstanceCount(instances);
		requestRequest.setInstanceCount(instances - instancesSet.size());

		LaunchSpecification launchSpecification = new LaunchSpecification();
		launchSpecification.setImageId(ami);
		launchSpecification.setInstanceType(size);
		if (userData != null)
			launchSpecification.setUserData(Base64.encodeAsString(userData
					.getBytes()));

		BlockDeviceMapping blockDeviceMapping = new BlockDeviceMapping();
		blockDeviceMapping.setDeviceName("/dev/sda1");

		EbsBlockDevice ebs = new EbsBlockDevice();
		ebs.setDeleteOnTermination(Boolean.TRUE);
		ebs.setVolumeSize(diskSize);
		blockDeviceMapping.setEbs(ebs);

		ArrayList<BlockDeviceMapping> blockList = new ArrayList<BlockDeviceMapping>();
		blockList.add(blockDeviceMapping);

		launchSpecification.setBlockDeviceMappings(blockList);

		ArrayList<String> securityGroups = new ArrayList<String>();
		securityGroups.add(Configuration.SECURITY_GROUP_NAME);
		launchSpecification.setSecurityGroups(securityGroups);

		launchSpecification.setKeyName(keyName);

		requestRequest.setLaunchSpecification(launchSpecification);

		RequestSpotInstancesResult requestResult = client
				.requestSpotInstances(requestRequest);

		List<SpotInstanceRequest> reqs = requestResult
				.getSpotInstanceRequests();
		for (SpotInstanceRequest req : reqs)
			instancesSet.add(new Instance(this, req.getInstanceId(), req
					.getSpotInstanceRequestId()));
	}

	public void onDemandRequest() {
		if (instances <= instancesSet.size()) {
			logger.info("No more instances will be launched because there are already enough.");
			return;
		}

		com.amazonaws.services.ec2.AmazonEC2 client = AmazonEC2.connect();

		RunInstancesRequest request = new RunInstancesRequest();

//		request.setMinCount(instances);
//		request.setMaxCount(instances);
		request.setMinCount(instances - instancesSet.size());
		request.setMaxCount(instances - instancesSet.size());
		request.setImageId(ami);
		request.setInstanceType(size);
		if (userData != null)
			request.setUserData(Base64.encodeAsString(userData
					.getBytes()));

		BlockDeviceMapping blockDeviceMapping = new BlockDeviceMapping();
		blockDeviceMapping.setDeviceName("/dev/sda1");

		EbsBlockDevice ebs = new EbsBlockDevice();
		ebs.setDeleteOnTermination(Boolean.TRUE);
		ebs.setVolumeSize(diskSize);
		blockDeviceMapping.setEbs(ebs);

		ArrayList<BlockDeviceMapping> blockList = new ArrayList<BlockDeviceMapping>();
		blockList.add(blockDeviceMapping);

		request.setBlockDeviceMappings(blockList);

		ArrayList<String> securityGroups = new ArrayList<String>();
		securityGroups.add(Configuration.SECURITY_GROUP_NAME);

		request.setSecurityGroups(securityGroups);
		request.setKeyName(keyName);

		RunInstancesResult requestResult = client.runInstances(request);

		Reservation reservation = requestResult.getReservation();
		reservation.getInstances();

		for (com.amazonaws.services.ec2.model.Instance i : reservation.getInstances())
			instancesSet.add(new Instance(this, i.getInstanceId(), null));
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

	public void setNameToInstances() {
		setNameToInstances(name);
	}

	public void setNameToInstances(String name) {
		for (Instance i : instancesSet)
			i.setName(name);
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

	public void addRunningInstance(String id, String spotRequestId) {
		for (Instance i : instancesSet)
			if (i.id.equals(id))
				return;

		instancesSet.add(new Instance(this, id, spotRequestId));
	}

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

				count++;
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

	public void retrieveMetrics(String localPath, Date date, int period, Statistic statistic, StandardUnit unit) throws Exception {
		String metricsToBeGet = getParameter("METRICS");
		if (metricsToBeGet != null)
			retrieveMetrics(metricsToBeGet.split(";"), localPath, date, period, statistic, unit);
	}

	public void retrieveMetrics(String[] metricsToBeGet, String localPath, Date date, int period, Statistic statistic, StandardUnit unit) throws Exception {
		int count = 1;
		if (metricsToBeGet != null && metricsToBeGet.length > 0)
			for (Instance i : instancesSet) {
				for (String s : metricsToBeGet) {
					Path file = Paths.get(localPath, i.getName() + count, s + ".csv");
					file.toFile().getParentFile().mkdirs();

					CloudWatch.writeInstanceMetricToFile(file, s, i.id, date, period, statistic, unit);
				}

				count++;
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

	public static class FirewallRule {

		public int from;
		public int to;
		public String ip;
		public String protocol;

		public FirewallRule(String ip, int from, int to, String protocol) {
			this.from = from;
			this.to = to;
			this.ip = ip;
			this.protocol = protocol;
		}

		public String toString() {
			return String.format("%s\t%d\t%d\t%s", ip, from, to, protocol);
		}
	}

	public static class Instance implements it.cloud.Instance {
		public String id;
		public String spotRequestId;

		private VirtualMachine vm;

		private String ip;

		public String getParameter(String name) {
			return vm.getParameter(name);
		}


		@Override
		public String getName() {
			return vm.name;
		}

		@Override
		public Path getKey() {
			return Configuration.getPathToFile(vm.keyName + ".pem");
		}

		@Override
		public String getSshUser() {
			return vm.sshUser;
		}

		@Override
		public String getSshPassword() {
			return vm.sshPassword;
		}

		public List<String> exec(String cmd) throws Exception {
			return Ssh.exec(this, cmd);
		}

		public void receiveFile(String lfile, String rfile) throws Exception {
			Ssh.receiveFile(this, lfile, rfile);
		}

		public void sendFile(String lfile, String rfile) throws Exception {
			Ssh.sendFile(this, lfile, rfile);
		}

		public void reboot() {
			com.amazonaws.services.ec2.AmazonEC2 client = AmazonEC2.connect();

			RebootInstancesRequest req = new RebootInstancesRequest();
			List<String> instanceIds = new ArrayList<String>();
			instanceIds.add(id);
			req.setInstanceIds(instanceIds);

			client.rebootInstances(req);
		}

		public void setName(String name) {
			com.amazonaws.services.ec2.AmazonEC2 client = AmazonEC2.connect();

			CreateTagsRequest req = new CreateTagsRequest();
			List<String> instanceIds = new ArrayList<String>();
			instanceIds.add(id);
			req.withResources(instanceIds);

			List<Tag> tags = new ArrayList<Tag>();
			tags.add(new Tag("Name", name));
			req.setTags(tags);

			client.createTags(req);
		}

		private Instance(VirtualMachine vm, String id, String spotRequestId) {
			this.id = id;
			this.spotRequestId = spotRequestId;
			this.vm = vm;

			ip = null;
		}

		@Override
		public String getIp() {
			if (ip != null)
				return ip;

			if (id == null) {
				getSpotStatus();
				if (id == null)
					return null;
			}

			com.amazonaws.services.ec2.AmazonEC2 client = AmazonEC2.connect();

			DescribeInstancesRequest instanceReq = new DescribeInstancesRequest();
			List<String> instanceIds = new ArrayList<String>();
			instanceIds.add(id);
			instanceReq.setInstanceIds(instanceIds);

			DescribeInstancesResult instanceRes = client
					.describeInstances(instanceReq);

			try {
				ip = instanceRes.getReservations().get(0).getInstances().get(0)
						.getPublicIpAddress();
			} catch (Exception e) {
				logger.error("Error while getting the IP.", e);
				ip = null;
			}

			return ip;
		}

		public SpotState getSpotStatus() {
			if (spotRequestId == null)
				return SpotState.SPOT_REQUEST_NOT_FOUND;

			com.amazonaws.services.ec2.AmazonEC2 client = AmazonEC2.connect();

			DescribeSpotInstanceRequestsRequest spotInstanceReq = new DescribeSpotInstanceRequestsRequest();
			List<String> spotInstanceRequestIds = new ArrayList<String>();
			spotInstanceRequestIds.add(spotRequestId);
			spotInstanceReq.setSpotInstanceRequestIds(spotInstanceRequestIds);
			DescribeSpotInstanceRequestsResult res = client
					.describeSpotInstanceRequests(spotInstanceReq);

			List<SpotInstanceRequest> reqs = res.getSpotInstanceRequests();
			if (reqs.size() > 0) {
				SpotInstanceRequest req = reqs.get(0);
				id = req.getInstanceId();
				return SpotState.valueFromRequest(req);
			} else {
				logger.error("No spot request found for the given id ("
						+ spotRequestId + ").");
				return SpotState.SPOT_REQUEST_NOT_FOUND;
			}
		}

		public InstanceStatus getInstanceStatus() {
			if (id == null) {
				getSpotStatus();
				if (id == null)
					return InstanceStatus.INSTANCE_NOT_FOUND;
			}

			com.amazonaws.services.ec2.AmazonEC2 client = AmazonEC2.connect();

			DescribeInstanceStatusRequest instanceReq = new DescribeInstanceStatusRequest();
			List<String> instanceIds = new ArrayList<String>();
			instanceIds.add(id);
			instanceReq.setInstanceIds(instanceIds);
			DescribeInstanceStatusResult instanceRes = client
					.describeInstanceStatus(instanceReq);

			List<com.amazonaws.services.ec2.model.InstanceStatus> reqs = instanceRes
					.getInstanceStatuses();
			if (reqs.size() > 0)
				return InstanceStatus.valueFromStatus(reqs.get(0));
			else {
				logger.error("No instance found for the given id (" + id + ").");
				return InstanceStatus.INSTANCE_NOT_FOUND;
			}
		}

		public static InstanceStatus getInstanceStatus(String id) {
			if (id == null) {
				return InstanceStatus.INSTANCE_NOT_FOUND;
			}

			com.amazonaws.services.ec2.AmazonEC2 client = AmazonEC2.connect();

			DescribeInstanceStatusRequest instanceReq = new DescribeInstanceStatusRequest();
			List<String> instanceIds = new ArrayList<String>();
			instanceIds.add(id);
			instanceReq.setInstanceIds(instanceIds);
			DescribeInstanceStatusResult instanceRes;
			try {
				instanceRes = client
						.describeInstanceStatus(instanceReq);
			} catch (Exception e) {
				logger.error("No instance found for the given id (" + id + ").");
				return InstanceStatus.INSTANCE_NOT_FOUND;
			}

			List<com.amazonaws.services.ec2.model.InstanceStatus> reqs = instanceRes
					.getInstanceStatuses();
			if (reqs.size() > 0)
				return InstanceStatus.valueFromStatus(reqs.get(0));
			else {
				logger.error("No instance found for the given id (" + id + ").");
				return InstanceStatus.INSTANCE_NOT_FOUND;
			}
		}

		public void terminate() throws AmazonServiceException {
			terminateSpotRequest();
			terminateInstance();

			ip = null;
		}

		private void terminateSpotRequest() throws AmazonServiceException {
			if (spotRequestId == null)
				return;

			com.amazonaws.services.ec2.AmazonEC2 client = AmazonEC2.connect();

			List<String> spotInstanceRequestIds = new ArrayList<String>();
			spotInstanceRequestIds.add(spotRequestId);

			CancelSpotInstanceRequestsRequest cancelRequest = new CancelSpotInstanceRequestsRequest(
					spotInstanceRequestIds);
			client.cancelSpotInstanceRequests(cancelRequest);
		}

		private void terminateInstance() throws AmazonServiceException {
			if (id == null)
				return;

			com.amazonaws.services.ec2.AmazonEC2 client = AmazonEC2.connect();

			List<String> instanceIds = new ArrayList<String>();
			instanceIds.add(id);

			TerminateInstancesRequest terminateRequest = new TerminateInstancesRequest(
					instanceIds);
			client.terminateInstances(terminateRequest);
		}

		public boolean waitUntilRunning() {
			return waitUntilRunning(false);
		}

		public boolean waitUntilRunning(boolean initializedIsEnough) {
			SpotState spotState = getSpotStatus();

			while (spotState == SpotState.OPEN) {
				try {
					Thread.sleep(10 * 1000);
					spotState = getSpotStatus();
				} catch (InterruptedException e) {
					logger.error("Error while waiting.", e);
				}
			}

			if (spotState != SpotState.ACTIVE) {
				if (spotState == SpotState.SPOT_REQUEST_NOT_FOUND && id != null) {
					logger.debug("Spot request not found, but maybe you started it as an on demand instance...");
				} else {
					logger.error("The spot request failed to start and is in the "
							+ spotState.getState() + " state!");
					return false;
				}
			}

//			try {
//				Thread.sleep(10 * 1000);
//			} catch (InterruptedException e) {
//				logger.error("Error while waiting.", e);
//			}

			InstanceStatus instanceStatus = getInstanceStatus();

			while (instanceStatus == InstanceStatus.INSTANCE_NOT_FOUND
					|| instanceStatus == InstanceStatus.INITIALIZING) {
				try {
					if (instanceStatus == InstanceStatus.INITIALIZING
							&& initializedIsEnough) {
						instanceStatus = InstanceStatus.OK;
					} else {
						Thread.sleep(10 * 1000);
						instanceStatus = getInstanceStatus();
					}
				} catch (InterruptedException e) {
					logger.error("Error while waiting.", e);
				}
			}
			if (instanceStatus != InstanceStatus.OK) {
				logger.error("The instance is in the "
						+ instanceStatus.getStatus() + " state!");
				return false;
			}

			return true;
		}

		public static final long TIMEOUT = 60000;

		public boolean waitUntilSshAvailable() {
			long init = System.currentTimeMillis();
			long end = init;
			boolean done = false;
			while (!done && ((end - init) <= TIMEOUT)) {
				try {
					exec("echo foo > /dev/null");
					done = true;
				} catch (Exception e) {
					try {
						Thread.sleep(1000);
					} catch (Exception e1) { }
				}
				end = System.currentTimeMillis();
			}
			return done;
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
	}

	public static enum InstanceStatus {
		INITIALIZING("initializing"), OK("ok"), INSTANCE_NOT_FOUND(
				"instance not found"), ERROR("error");

		String status;

		private InstanceStatus(String status) {
			this.status = status;
		}

		public String getStatus() {
			return status;
		}

		public static InstanceStatus valueFromStatus(String status) {
			InstanceStatus[] values = InstanceStatus.values();
			for (InstanceStatus i : values)
				if (i.status.equals(status))
					return i;
			return ERROR;
		}

		public static InstanceStatus valueFromStatus(
				com.amazonaws.services.ec2.model.InstanceStatus status) {
			return valueFromStatus(status.getInstanceStatus().getStatus());
		}
	}

	public static enum SpotState {
		OPEN("open"), ACTIVE("active"), CANCELLED("cancelled"), FAILED("failed"), SPOT_REQUEST_NOT_FOUND(
				"spot request not found"), ERROR("error");

		String state;

		private SpotState(String state) {
			this.state = state;
		}

		public static SpotState valueFromRequest(
				SpotInstanceRequest spotInstanceRequest) {
			return valueFromState(spotInstanceRequest.getState());
		}

		public String getState() {
			return state;
		}

		public static SpotState valueFromState(String state) {
			SpotState[] values = SpotState.values();
			for (SpotState i : values)
				if (i.state.equals(state))
					return i;
			return ERROR;
		}
	}

}
