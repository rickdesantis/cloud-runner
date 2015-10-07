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

import com.amazonaws.services.cloudwatch.model.StandardUnit;
import com.amazonaws.services.cloudwatch.model.Statistic;
import com.amazonaws.services.ec2.model.BlockDeviceMapping;
import com.amazonaws.services.ec2.model.EbsBlockDevice;
import com.amazonaws.services.ec2.model.LaunchSpecification;
import com.amazonaws.services.ec2.model.RequestSpotInstancesRequest;
import com.amazonaws.services.ec2.model.RequestSpotInstancesResult;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.SpotInstanceRequest;
import com.amazonaws.util.Base64;

import it.cloud.amazon.Configuration;
import it.cloud.amazon.cloudwatch.CloudWatch;
import it.cloud.utils.CloudException;
import it.cloud.utils.ConfigurationFile;

public class VirtualMachine extends it.cloud.VirtualMachine {

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
		super(name, ami, instances, size, diskSize, maxPrice, os, keyName, sshUser, sshPassword, otherParams);
		
		if (Configuration.AWS_CREDENTIALS == null)
			throw new CloudException(
					"You didn't provide a valid credentials file, aborting.");

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

	public void setNameToInstances() {
		setNameToInstances(name);
	}

	public void setNameToInstances(String name) {
		for (it.cloud.Instance i : instancesSet)
			((Instance)i).setName(name);
	}

	@Override
	public void addRunningInstance(String id, String spotRequestId) {
		for (it.cloud.Instance i : instancesSet)
			if (i.id.equals(id))
				return;

		instancesSet.add(new Instance(this, id, spotRequestId));
	}
	
	private static int getSuggestedPeriod(Date date) {
		Date now = new Date();
		double diff = now.getTime() - date.getTime();
		diff /= 1000 * 60;
		final int maxData = 1440;

		double res = diff / maxData;
		res = Math.ceil(res);

		if (res < 1)
			res = 1;

		return (int)res * 60;
	}
	
	public static void retrieveMetrics(List<String> ids, VirtualMachine vm, String localPath, Date date) throws Exception {
		retrieveMetrics(ids, vm, localPath, date, getSuggestedPeriod(date), Statistic.Average, null);
	}
	
	public static void retrieveMetrics(List<String> ids, VirtualMachine vm, String localPath, Date date, int period, Statistic statistic, StandardUnit unit) throws Exception {
		String metricsToBeGet = vm.getParameter("METRICS");
		if (metricsToBeGet != null)
			retrieveMetrics(ids, vm, metricsToBeGet.split(";"), localPath, date, period, statistic, unit);
	}
	
	public static void retrieveMetrics(List<String> ids, VirtualMachine vm, String[] metricsToBeGet, String localPath, Date date, int period, Statistic statistic, StandardUnit unit) throws Exception {
		if (ids.size() == 0)
			return;
		
		int count = 1;
		String name = vm.getParameter("NAME");
		
		if (metricsToBeGet != null && metricsToBeGet.length > 0)
			for (String id : ids) {
				for (String s : metricsToBeGet) {
					Path file = Paths.get(localPath, name + count, s + ".csv");
					file.toFile().getParentFile().mkdirs();

					CloudWatch.writeInstanceMetricToFile(file, s, id, date, period, statistic, unit);
				}

				++count;
			}
	}
	
	public void retrieveMetrics(String localPath, Date date) throws Exception {
		retrieveMetrics(localPath, date, getSuggestedPeriod(date), Statistic.Average, null);
	}

	public void retrieveMetrics(String localPath, Date date, int period, Statistic statistic, StandardUnit unit) throws Exception {
		String metricsToBeGet = getParameter("METRICS");
		if (metricsToBeGet != null)
			retrieveMetrics(metricsToBeGet.split(";"), localPath, date, period, statistic, unit);
	}

	public void retrieveMetrics(String[] metricsToBeGet, String localPath, Date date, int period, Statistic statistic, StandardUnit unit) throws Exception {
		int count = 1;
		if (metricsToBeGet != null && metricsToBeGet.length > 0)
			for (it.cloud.Instance i : instancesSet) {
				for (String s : metricsToBeGet) {
					Path file = Paths.get(localPath, i.getName() + count, s + ".csv");
					file.toFile().getParentFile().mkdirs();

					CloudWatch.writeInstanceMetricToFile(file, s, i.id, date, period, statistic, unit);
				}

				++count;
			}
	}
	
	@Override
	public String getIp(String id) {
		return Instance.getIp(id);
	}

}
