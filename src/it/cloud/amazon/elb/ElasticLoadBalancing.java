package it.cloud.amazon.elb;

import it.cloud.amazon.ec2.Configuration;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerResult;
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest;

public class ElasticLoadBalancing {

	@SuppressWarnings("unused")
	private static final Logger logger = LoggerFactory.getLogger(ElasticLoadBalancing.class);

	private static AmazonElasticLoadBalancingClient client = null;

	public static AmazonElasticLoadBalancingClient connect() {
		if (client == null) {
			Region r = Region.getRegion(Regions.fromName(Configuration.REGION));

			client = new AmazonElasticLoadBalancingClient(
					Configuration.AWS_CREDENTIALS);
			client.setRegion(r);
		}
		return client;
	}

	public static String createNewLoadBalancer(String name) {
		if (name == null || name.trim().length() == 0)
			throw new RuntimeException(
					"The name of the load balancer cannot be empty!");

		connect();

		CreateLoadBalancerRequest req = new CreateLoadBalancerRequest();
		req.setLoadBalancerName(name);
		CreateLoadBalancerResult res = client.createLoadBalancer(req);

		return res.getDNSName();
	}
	
	public static void deleteLoadBalancer(String name) {
		if (name == null || name.trim().length() == 0)
			throw new RuntimeException(
					"The name of the load balancer cannot be empty!");

		connect();

		DeleteLoadBalancerRequest req = new DeleteLoadBalancerRequest();
		req.setLoadBalancerName(name);
		client.deleteLoadBalancer(req);
	}

	public static void addInstancesToLoadBalancer(String name,
			String... instanceIds) {
		if (name == null || name.trim().length() == 0)
			throw new RuntimeException(
					"The name of the load balancer cannot be empty!");

		if (instanceIds == null || instanceIds.length == 0)
			throw new RuntimeException(
					"You need to specify at least one instance id!");
		
		connect();

		ArrayList<com.amazonaws.services.elasticloadbalancing.model.Instance> instances = new ArrayList<com.amazonaws.services.elasticloadbalancing.model.Instance>();
		for (String instanceId : instanceIds)
			instances
					.add(new com.amazonaws.services.elasticloadbalancing.model.Instance(
							instanceId));

		RegisterInstancesWithLoadBalancerRequest req = new RegisterInstancesWithLoadBalancerRequest(
				name, instances);
		client.registerInstancesWithLoadBalancer(req);
	}
	
	public static void removeInstancesFromLoadBalancer(String name,
			String... instanceIds) {
		if (name == null || name.trim().length() == 0)
			throw new RuntimeException(
					"The name of the load balancer cannot be empty!");

		if (instanceIds == null || instanceIds.length == 0)
			throw new RuntimeException(
					"You need to specify at least one instance id!");
		
		connect();

		ArrayList<com.amazonaws.services.elasticloadbalancing.model.Instance> instances = new ArrayList<com.amazonaws.services.elasticloadbalancing.model.Instance>();
		for (String instanceId : instanceIds)
			instances
					.add(new com.amazonaws.services.elasticloadbalancing.model.Instance(
							instanceId));

		DeregisterInstancesFromLoadBalancerRequest req = new DeregisterInstancesFromLoadBalancerRequest(
				name, instances);
		client.deregisterInstancesFromLoadBalancer(req);
	}

}
