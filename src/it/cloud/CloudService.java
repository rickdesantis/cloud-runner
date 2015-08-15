package it.cloud;

import java.util.List;

public interface CloudService {
	public List<Instance> getRunningMachinesByImageId(String imageId);
	public List<Instance> startMachines(int n, String imageId);
	public void addRunningInstances(VirtualMachine vm);
	public String getVMNameByImageId(String imageId);
	
	public static final String PROVIDERS_KEY = "providers";
	public static final String REGION_KEY = "region";
	public static final String PRICE_MARGIN_KEY = "price_margin";
	public static final String SECURITY_GROUP_NAME_KEY = "security_group_name";
	public static final String CREDENTIALS_KEY = "credentials";
}
