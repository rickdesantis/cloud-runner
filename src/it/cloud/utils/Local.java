package it.cloud.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Local {
	
	protected static final Logger logger = LoggerFactory.getLogger(Local.class);

	public static List<String> exec(String command) throws Exception {
		final List<String> res = new ArrayList<String>();
		
		long init = System.currentTimeMillis();
		
		ProcessBuilder pb = new ProcessBuilder(new String[] { "bash", "-c", command });
		pb.redirectErrorStream(true);

		final Process p = pb.start();
		
		Thread in = new Thread() {
			public void run() {
				try (Scanner sc = new Scanner(p.getInputStream())) {
					while (sc.hasNextLine()) {
						String line = sc.nextLine();
						logger.trace(line);
						res.add(line);
					}
				}
			}
		};
		in.start();
		
		Thread err = new Thread() {
			public void run() {
				try (Scanner sc = new Scanner(p.getErrorStream())) {
					while (sc.hasNextLine()) {
						String line = sc.nextLine();
						logger.trace(line);
						res.add(line);
					}
				}
			}
		};
		err.start();

		in.join();
		err.join();
		
		res.add(String.format("exit-status: %d", p.waitFor()));
		
		long duration = System.currentTimeMillis() - init;
		logger.debug("Executed `{}` on {} in {}", command, "localhost", Utilities.durationToString(duration));
		return res;
	}
	
	public static Thread execInBackground(String command)
			throws Exception {
		final String fcommand = command;

		Thread t = new Thread() {
			public void run() {
				try {
					exec(fcommand);
				} catch (Exception e) {
					logger.error("Error while executing the command.", e);
				}
			}
		};
		t.start();
		return t;
	}

}
