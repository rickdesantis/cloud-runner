package it.cloud.amazon.ec2;

import it.cloud.utils.CloudException;
import it.cloud.utils.Ssh;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.services.ec2.model.BlockDeviceMapping;
import com.amazonaws.services.ec2.model.CancelSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesRequest;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsResult;
import com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryRequest;
import com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryResult;
import com.amazonaws.services.ec2.model.EbsBlockDevice;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.LaunchSpecification;
import com.amazonaws.services.ec2.model.RequestSpotInstancesRequest;
import com.amazonaws.services.ec2.model.RequestSpotInstancesResult;
import com.amazonaws.services.ec2.model.SpotInstanceRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.util.Base64;

public class VirtualMachine {

	private static final Logger logger = LoggerFactory.getLogger(VirtualMachine.class);

	public static final double PRICE_MARGIN = 0.2;

	private static AmazonEC2 client = null;

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
			Properties prop = new Properties();
			prop.load(Configuration.getInputStream(Configuration.CONFIGURATION));

			String ami = prop.getProperty(name + "_AMI");
			String size = prop.getProperty(name + "_SIZE");
			if (overrideSize != null)
				size = overrideSize;
			String instances = prop.getProperty(name + "_INSTANCES");
			if (overrideInstances > 0)
				instances = Integer.valueOf(overrideInstances).toString();
			String diskSize = prop.getProperty(name + "_DISK");
			String os = prop.getProperty(name + "_OS");
			String keyName = prop.getProperty(name + "_KEYPAIR_NAME");
			String sshUser = prop.getProperty(name + "_SSH_USER");
			String sshPassword = prop.getProperty(name + "_SSH_PASS");

			Map<String, String> otherParams = new HashMap<String, String>();

			for (Object o : prop.keySet()) {
				String key = (String) o;
				if (key.startsWith(name + "_"))
					otherParams.put(key.substring((name + "_").length()),
							prop.getProperty(key));
			}

			if (ami != null && size != null && instances != null
					&& diskSize != null && os != null && keyName != null
					&& sshUser != null && sshPassword != null) {
				double[] pricesInRegion = getPricesInRegion(size, os);
				if (pricesInRegion.length == 0)
					return null;
				double maxPrice = pricesInRegion[0];
				for (int i = 1; i < pricesInRegion.length; ++i)
					if (pricesInRegion[i] > maxPrice)
						maxPrice = pricesInRegion[i];

				maxPrice += PRICE_MARGIN;

				return new VirtualMachine(name, ami,
						Integer.valueOf(instances), size,
						Integer.valueOf(diskSize), maxPrice, os, keyName,
						sshUser, sshPassword, otherParams);
			}
		} catch (Exception e) {
			throw new CloudException("Error while loading the configuration.",
					e);
		}
		throw new CloudException("VM not found in the configuration file!");
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

		connect();

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

	private static List<FirewallRule> firewallRules;
	static {
		firewallRules = new ArrayList<FirewallRule>();

		try (Scanner sc = new Scanner(
				Configuration.getInputStream(Configuration.FIREWALL_RULES));) {
			if (sc.hasNextLine())
				sc.nextLine();

			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				String[] fields = line.split(",");
				try {
					firewallRules.add(new FirewallRule(fields[0], Integer
							.valueOf(fields[1]), Integer.valueOf(fields[2]),
							fields[3]));
				} catch (Exception e) {
				}
			}
		}

		createSecurityGroup();
	}

	public static AmazonEC2 connect() {
		if (client == null) {
			Region r = Region.getRegion(Regions.fromName(Configuration.REGION));

			client = new AmazonEC2Client(Configuration.AWS_CREDENTIALS);
			client.setRegion(r);
		}
		return client;
	}

	private static void createSecurityGroup() {
		if (Paths.get(Configuration.SECURITY_GROUP_FILE_NAME).toFile().exists())
			return;

		connect();

		try {
			CreateSecurityGroupRequest securityGroupRequest = new CreateSecurityGroupRequest(
					Configuration.SECURITY_GROUP_NAME,
					Configuration.SECURITY_GROUP_DESC);
			client.createSecurityGroup(securityGroupRequest);
		} catch (AmazonServiceException e) {
			logger.error(
					"Error while creating the security group: it probably already exists.",
					e);
		}

		ArrayList<IpPermission> ipPermissions = new ArrayList<IpPermission>();

		for (FirewallRule rule : firewallRules) {
			IpPermission ipPermission = new IpPermission();
			ipPermission.setIpProtocol(rule.protocol);
			ipPermission.setFromPort(new Integer(rule.from));
			ipPermission.setToPort(new Integer(rule.to));
			ArrayList<String> ipRanges = new ArrayList<String>();
			ipRanges.add(rule.ip);
			ipPermission.setIpRanges(ipRanges);
			ipPermissions.add(ipPermission);
		}

		try {
			AuthorizeSecurityGroupIngressRequest ingressRequest = new AuthorizeSecurityGroupIngressRequest(
					Configuration.SECURITY_GROUP_NAME, ipPermissions);
			client.authorizeSecurityGroupIngress(ingressRequest);
		} catch (AmazonServiceException e) {
			logger.error(
					"Error while setting the security group: it probably was already set.",
					e);
		}

		try (PrintWriter out = new PrintWriter(new FileOutputStream(Paths.get(
				Configuration.SECURITY_GROUP_FILE_NAME).toFile()));) {
			out.println("done");
		} catch (Exception e) {
			logger.error("Error while creating the file.", e);
		}
	}

	public static double[] getPricesInRegion(String size, String os) {
		connect();

		DescribeAvailabilityZonesRequest availabilityZoneReq = new DescribeAvailabilityZonesRequest();
		DescribeAvailabilityZonesResult result = client
				.describeAvailabilityZones(availabilityZoneReq);
		List<AvailabilityZone> availabilityZones = result
				.getAvailabilityZones();

		double res[] = new double[availabilityZones.size()];
		int i = 0;

		for (AvailabilityZone zone : availabilityZones) {
			DescribeSpotPriceHistoryRequest req = new DescribeSpotPriceHistoryRequest();

			List<String> instanceTypes = new ArrayList<String>();
			instanceTypes.add(size);
			req.setInstanceTypes(instanceTypes);

			List<String> productDescriptions = new ArrayList<String>();
			productDescriptions.add(os);
			req.setProductDescriptions(productDescriptions);

			req.setAvailabilityZone(zone.getZoneName());

			req.setMaxResults(1);

			DescribeSpotPriceHistoryResult priceResult = client
					.describeSpotPriceHistory(req);
			res[i] = Double.parseDouble(priceResult.getSpotPriceHistory()
					.get(0).getSpotPrice());

			logger.debug("Zone: {}, Price: {}", zone.getZoneName(),
					(float) res[i]);

			i++;
		}

		return res;
	}

	public void spotRequest() {
		connect();

		RequestSpotInstancesRequest requestRequest = new RequestSpotInstancesRequest();

		requestRequest.setSpotPrice(Double.valueOf(maxPrice).toString());
		requestRequest.setInstanceCount(instances);

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

	public void terminateAllSpots() {
		if (instancesSet.size() == 0)
			return;

		for (Instance i : instancesSet)
			i.terminate();

		instancesSet.clear();
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

		public String getSshUser() {
			return vm.sshUser;
		}

		public String getSshPassword() {
			return vm.sshPassword;
		}

		public List<String> exec(String command) throws Exception {
			return Ssh.exec(this, command);
		}

		public void receiveFile(String lfile, String rfile) throws Exception {
			Ssh.receiveFile(this, lfile, rfile);
		}

		public void sendFile(String lfile, String rfile) throws Exception {
			Ssh.sendFile(this, lfile, rfile);
		}

		private Instance(VirtualMachine vm, String id, String spotRequestId) {
			this.id = id;
			this.spotRequestId = spotRequestId;
			this.vm = vm;

			ip = null;
		}

		public String getIp() {
			if (ip != null)
				return ip;

			if (id == null) {
				getSpotStatus();
				if (id == null)
					return null;
			}

			connect();

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

			connect();

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

			connect();

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

		public void terminate() throws AmazonServiceException {
			terminateSpotRequest();
			terminateInstance();

			ip = null;
		}

		private void terminateSpotRequest() throws AmazonServiceException {
			connect();

			List<String> spotInstanceRequestIds = new ArrayList<String>();
			spotInstanceRequestIds.add(spotRequestId);

			CancelSpotInstanceRequestsRequest cancelRequest = new CancelSpotInstanceRequestsRequest(
					spotInstanceRequestIds);
			client.cancelSpotInstanceRequests(cancelRequest);
		}

		private void terminateInstance() throws AmazonServiceException {
			connect();

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
				logger.error("The spot request failed to start and is in the "
						+ spotState.getState() + " state!");
				return false;
			}

			try {
				Thread.sleep(10 * 1000);
			} catch (InterruptedException e) {
				logger.error("Error while waiting.", e);
			}

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
