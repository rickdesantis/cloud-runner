package it.cloud.amazon.elb;

import it.cloud.amazon.ec2.Configuration;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.CreateLoadBalancerResult;
import com.amazonaws.services.elasticloadbalancing.model.DeleteLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DeregisterInstancesFromLoadBalancerRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancing.model.LoadBalancerDescription;
import com.amazonaws.services.elasticloadbalancing.model.RegisterInstancesWithLoadBalancerRequest;

public class ElasticLoadBalancing {

	@SuppressWarnings("unused")
	private static final Logger logger = LoggerFactory
			.getLogger(ElasticLoadBalancing.class);

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

	public class Listener {
		public String protocol;
		public int port;

		public Listener(String protocol, int port) {
			if (!protocol.equals("HTTP") && !protocol.equals("HTTPS") && !protocol.equals("TCP") && !protocol.equals("SSL"))
				throw new RuntimeException("Protocol " + protocol + " not recognized!");
			
			this.protocol = protocol;
			this.port = port;
		}
	}

	public static String createNewLoadBalancer(String name,
			Listener... listeners) {
		if (name == null || name.trim().length() == 0)
			throw new RuntimeException(
					"The name of the load balancer cannot be empty!");

		connect();

		CreateLoadBalancerRequest req = new CreateLoadBalancerRequest();

		req.setLoadBalancerName(name);

		if (listeners != null && listeners.length > 0) {
			ArrayList<com.amazonaws.services.elasticloadbalancing.model.Listener> actualListeners = new ArrayList<com.amazonaws.services.elasticloadbalancing.model.Listener>();
			for (Listener listener : listeners)
				actualListeners
						.add(new com.amazonaws.services.elasticloadbalancing.model.Listener(
								listener.protocol, listener.port, listener.port));
			req.setListeners(actualListeners);
		}

		ArrayList<String> securityGroups = new ArrayList<String>();
		securityGroups.add(Configuration.SECURITY_GROUP_NAME);
		req.setSecurityGroups(securityGroups);

		CreateLoadBalancerResult res = client.createLoadBalancer(req);

		return res.getDNSName();
	}

	public static String getLoadBalancerDNS(String name) {
		if (name == null || name.trim().length() == 0)
			throw new RuntimeException(
					"The name of the load balancer cannot be empty!");

		connect();

		ArrayList<String> names = new ArrayList<String>();
		names.add(name);

		DescribeLoadBalancersRequest req = new DescribeLoadBalancersRequest(
				names);
		DescribeLoadBalancersResult res = client.describeLoadBalancers(req);
		List<LoadBalancerDescription> descs = res.getLoadBalancerDescriptions();
		if (descs.size() == 0 || descs.get(0) == null)
			return null;

		return descs.get(0).getDNSName();
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
