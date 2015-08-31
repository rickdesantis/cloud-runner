package it.cloud.amazon.ec2;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import com.amazonaws.services.ec2.model.AvailabilityZone;
import com.amazonaws.services.ec2.model.CreateSecurityGroupRequest;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesRequest;
import com.amazonaws.services.ec2.model.DescribeAvailabilityZonesResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeSecurityGroupsResult;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsResult;
import com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryRequest;
import com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.SpotInstanceRequest;

import it.cloud.CloudService;
import it.cloud.Instance;
import it.cloud.VirtualMachine;
import it.cloud.amazon.Configuration;
import it.cloud.amazon.ec2.Instance.InstanceStatus;
import it.cloud.utils.CloudException;
import it.cloud.utils.ConfigurationFile;
import it.cloud.utils.FirewallRule;

public class AmazonEC2 implements CloudService {
	
	private static final Logger logger = LoggerFactory.getLogger(AmazonEC2.class);

	private static com.amazonaws.services.ec2.AmazonEC2 client = null;
	
	public static com.amazonaws.services.ec2.AmazonEC2 connect() {
		if (client == null) {
			Region r = Region.getRegion(Regions.fromName(Configuration.REGION));

			client = new AmazonEC2Client(Configuration.AWS_CREDENTIALS);
			client.setRegion(r);
		}
		return client;
	}
	
	static {
		createSecurityGroup();
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
			if (e.getErrorCode().equals("InvalidGroup.Duplicate"))
				logger.debug("The security group already exists.");
			else
				logger.error(
					"Error while creating the security group.",
					e);
		}

		ArrayList<IpPermission> ipPermissions = new ArrayList<IpPermission>();

		for (FirewallRule rule : FirewallRule.rules) {
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
			if (e.getErrorCode().equals("InvalidPermission.Duplicate"))
				logger.debug("The security group was already set.");
			else
				logger.error(
					"Error while setting the security group.",
					e);
		}

		try (PrintWriter out = new PrintWriter(new FileOutputStream(Paths.get(
				Configuration.SECURITY_GROUP_FILE_NAME).toFile()));) {
			out.println("done");
		} catch (Exception e) {
			logger.error("Error while creating the file.", e);
		}
	}
	
	private static Map<String, double[]> pricesInRegion = new HashMap<String, double[]>();

	public static double[] getPricesInRegion(String size, String os) {
		double[] resultInMap = pricesInRegion.get(size + "@" + os);
		if (resultInMap != null && resultInMap.length > 0)
			return resultInMap;
		
		connect();

		List<String> availabilityZones = getAllAvailabilityZones();

		double res[] = new double[availabilityZones.size()];
		int i = 0;

		for (String zone : availabilityZones) {
			DescribeSpotPriceHistoryRequest req = new DescribeSpotPriceHistoryRequest();

			List<String> instanceTypes = new ArrayList<String>();
			instanceTypes.add(size);
			req.setInstanceTypes(instanceTypes);

			List<String> productDescriptions = new ArrayList<String>();
			productDescriptions.add(os);
			req.setProductDescriptions(productDescriptions);

			req.setAvailabilityZone(zone);

			req.setMaxResults(1);

			DescribeSpotPriceHistoryResult priceResult = client
					.describeSpotPriceHistory(req);
			res[i] = Double.parseDouble(priceResult.getSpotPriceHistory()
					.get(0).getSpotPrice());

			logger.debug("Zone: {}, Price: {}", zone,
					(float) res[i]);

			i++;
		}

		pricesInRegion.put(size + "@" + os, res);
		return res;
	}
	
	@Override
	public String getVMNameByImageId(String imageId) {
		try {
			ConfigurationFile conf = ConfigurationFile.parse().getElement(VirtualMachine.MACHINES_KEY);
			
			for (String key : conf.keys()) {
				ConfigurationFile machine = conf.getElement(key);
				if (machine.getString(VirtualMachine.IMAGE_ID_KEY).equals(imageId))
					return key;
			}
			
		} catch (Exception e) { }
		
		return null;
	}

	@Override
	public List<Instance> getRunningMachinesByImageId(String imageId) {
		ArrayList<Instance> instances = new ArrayList<Instance>();
		
		com.amazonaws.services.ec2.AmazonEC2 client = connect();
		
		ArrayList<Filter> filters = new ArrayList<Filter>();
		filters.add(new Filter("launch.image-id", getAsList(imageId) ));
		
		it.cloud.amazon.ec2.VirtualMachine vm;
		try {
			vm = it.cloud.amazon.ec2.VirtualMachine.getVM(getVMNameByImageId(imageId)); // TODO: this will use the default size
		} catch (CloudException e) {
			logger.error("Image ID not defined in the configuration file!");
			return instances;
		}
		
		DescribeSpotInstanceRequestsRequest spotRequest = new DescribeSpotInstanceRequestsRequest();
		spotRequest.setFilters(filters);
		
		DescribeSpotInstanceRequestsResult spotResult = client.describeSpotInstanceRequests(spotRequest);
		for (SpotInstanceRequest req : spotResult.getSpotInstanceRequests())
			vm.addRunningInstance(req.getInstanceId(), req.getSpotInstanceRequestId());
		
		DescribeInstancesRequest instanceRequest = new DescribeInstancesRequest();
		instanceRequest.setFilters(filters);
		
		DescribeInstancesResult instanceResult = client.describeInstances(instanceRequest);
		for (Reservation req : instanceResult.getReservations())
			for (com.amazonaws.services.ec2.model.Instance i : req.getInstances())
				vm.addRunningInstance(i.getInstanceId(), null);
		
		instances.addAll(vm.getInstances());
		return instances;
	}

	@Override
	public List<Instance> startMachines(int n, String imageId) {
		ArrayList<Instance> instances = new ArrayList<Instance>();
		
		it.cloud.amazon.ec2.VirtualMachine vm;
		try {
			vm = it.cloud.amazon.ec2.VirtualMachine.getVM(getVMNameByImageId(imageId), null, n); // TODO: this will use the default size
		} catch (CloudException e) {
			logger.error("Image ID not defined in the configuration file!");
			return instances;
		}
		
		vm.spotRequest();
		vm.waitUntilRunning();
		instances.addAll(vm.getInstances());
		
		return instances;
	}
	
	private static List<String> getAsList(String... values) {
		ArrayList<String> res = new ArrayList<String>();
		for (String value : values)
			res.add(value);
		return res;
	}

	@Override
	public void addRunningInstances(VirtualMachine vm) {
		if (vm.getInstancesNeeded() <= vm.getInstancesRunning())
			return;
		
		com.amazonaws.services.ec2.AmazonEC2 client = connect();
		
		ArrayList<Filter> filters = new ArrayList<Filter>();
		filters.add(new Filter("launch.image-id", getAsList(vm.getImageId()) ));
		filters.add(new Filter("launch.instance-type", getAsList(vm.getSize()) ));
		
		DescribeSpotInstanceRequestsRequest spotRequest = new DescribeSpotInstanceRequestsRequest();
		spotRequest.setFilters(filters);
		
		DescribeSpotInstanceRequestsResult spotResult = client.describeSpotInstanceRequests(spotRequest);
		List<SpotInstanceRequest> reqs = spotResult.getSpotInstanceRequests();
		for (int i = 0; i < reqs.size() && vm.getInstancesNeeded() > vm.getInstancesRunning(); ++i) {
			SpotInstanceRequest req = reqs.get(i);
			InstanceStatus status = it.cloud.amazon.ec2.Instance.getInstanceStatus(req.getInstanceId());
			if (status == InstanceStatus.OK)
				vm.addRunningInstance(req.getInstanceId(), req.getSpotInstanceRequestId());
		}
		
		if (vm.getInstancesNeeded() <= vm.getInstancesRunning())
			return;
		
		filters = new ArrayList<Filter>();
		filters.add(new Filter("image-id", getAsList(vm.getImageId()) ));
		filters.add(new Filter("instance-type", getAsList(vm.getSize()) ));
		
		DescribeInstancesRequest instanceRequest = new DescribeInstancesRequest();
		instanceRequest.setFilters(filters);
		
		DescribeInstancesResult instanceResult = client.describeInstances(instanceRequest);
		List<Reservation> reservations = instanceResult.getReservations();
		for (int i = 0; i < reservations.size() && vm.getInstancesNeeded() > vm.getInstancesRunning(); ++i) {
			Reservation req = reservations.get(i);
			List<com.amazonaws.services.ec2.model.Instance> instances = req.getInstances();
			for (int j = 0; j < instances.size() && vm.getInstancesNeeded() > vm.getInstancesRunning(); ++j) {
				com.amazonaws.services.ec2.model.Instance instance = instances.get(j);
				InstanceStatus status = it.cloud.amazon.ec2.Instance.getInstanceStatus(instance.getInstanceId());
				if (status == InstanceStatus.OK)
					vm.addRunningInstance(instance.getInstanceId(), null);
			}
		}
	}
	
	public static List<String> getAllAvailabilityZones() {
		connect();
		
		DescribeAvailabilityZonesRequest req = new DescribeAvailabilityZonesRequest();
		
		ArrayList<Filter> filters = new ArrayList<Filter>();
		ArrayList<String> regions = new ArrayList<String>();
		regions.add(Configuration.REGION);
		filters.add(new Filter("region-name", regions));
		req.setFilters(filters);
		
		DescribeAvailabilityZonesResult res = client.describeAvailabilityZones(req);
		
		List<AvailabilityZone> zones = res.getAvailabilityZones();
		ArrayList<String> zonesStr = new ArrayList<String>();
		for (AvailabilityZone zone : zones)
			zonesStr.add(zone.getZoneName());
		
		return zonesStr;
	}
	
	public static String getSecurityGroupId() {
		connect();
		
		DescribeSecurityGroupsRequest req = new DescribeSecurityGroupsRequest();
		
		ArrayList<String> groupNames = new ArrayList<String>();
		groupNames.add(Configuration.SECURITY_GROUP_NAME);
		req.setGroupNames(groupNames);
		
		DescribeSecurityGroupsResult res = client.describeSecurityGroups(req);
		List<SecurityGroup> securityGroups = res.getSecurityGroups();
		
		if (securityGroups == null || securityGroups.size() == 0)
			return null;
		
		return securityGroups.get(0).getGroupId();
	}

}
