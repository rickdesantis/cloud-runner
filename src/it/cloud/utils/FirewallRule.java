package it.cloud.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import it.cloud.amazon.Configuration;

public class FirewallRule {

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
	
	public static List<FirewallRule> rules;
	static {
		rules = new ArrayList<FirewallRule>();

		try (Scanner sc = new Scanner(
				Configuration.getInputStream(Configuration.FIREWALL_RULES));) {
			if (sc.hasNextLine())
				sc.nextLine();

			while (sc.hasNextLine()) {
				String line = sc.nextLine();
				String[] fields = line.split(",");
				try {
					rules.add(new FirewallRule(fields[0], Integer
							.valueOf(fields[1]), Integer.valueOf(fields[2]),
							fields[3]));
				} catch (Exception e) {
				}
			}
		}
	}
}
