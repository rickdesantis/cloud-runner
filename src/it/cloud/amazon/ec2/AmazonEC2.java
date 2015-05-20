package it.cloud.amazon.ec2;

import it.cloud.CloudService;
import it.cloud.Instance;
import it.cloud.VirtualMachine;
import it.cloud.amazon.ec2.VirtualMachine.FirewallRule;
import it.cloud.amazon.ec2.VirtualMachine.InstanceStatus;
import it.cloud.utils.CloudException;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;

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
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsResult;
import com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryRequest;
import com.amazonaws.services.ec2.model.DescribeSpotPriceHistoryResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.IpPermission;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.SpotInstanceRequest;

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
	
	@Override
	public String getVMNameByImageId(String imageId) {
		try {
			Properties prop = new Properties();
			prop.load(Configuration.getInputStream(Configuration.CONFIGURATION));
			
			Enumeration<?> e = prop.propertyNames();
			while (e.hasMoreElements()) {
				String key = (String) e.nextElement(); 
				if (key.endsWith("_AMI") && prop.getProperty(key).equals(imageId)) {
					return key.substring(0, key.lastIndexOf("_AMI"));
				}
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
			InstanceStatus status = it.cloud.amazon.ec2.VirtualMachine.Instance.getInstanceStatus(req.getInstanceId());
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
				InstanceStatus status = it.cloud.amazon.ec2.VirtualMachine.Instance.getInstanceStatus(instance.getInstanceId());
				if (status == InstanceStatus.OK)
					vm.addRunningInstance(instance.getInstanceId(), null);
			}
		}
	}

}
