package it.cloud.amazon.ec2;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.ec2.model.CancelSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusRequest;
import com.amazonaws.services.ec2.model.DescribeInstanceStatusResult;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsRequest;
import com.amazonaws.services.ec2.model.DescribeSpotInstanceRequestsResult;
import com.amazonaws.services.ec2.model.RebootInstancesRequest;
import com.amazonaws.services.ec2.model.SpotInstanceRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;

public class Instance extends it.cloud.Instance {
	
	public String spotRequestId;

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

	Instance(VirtualMachine vm, String id, String spotRequestId) {
		super(vm, id);
		this.spotRequestId = spotRequestId;
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

//		try {
//			Thread.sleep(10 * 1000);
//		} catch (InterruptedException e) {
//			logger.error("Error while waiting.", e);
//		}

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
