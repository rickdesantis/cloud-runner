package it.cloud.amazon.ec2;

import java.util.List;

import it.cloud.CloudService;
import it.cloud.Instance;

public class AmazonEC2 implements CloudService {

	public AmazonEC2() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public List<Instance> getRunningMachinesByImageId(String imageId) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<Instance> startMachines(int n, String imageId) {
		// TODO Auto-generated method stub
		return null;
	}

}
