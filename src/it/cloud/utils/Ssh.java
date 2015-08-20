package it.cloud.utils;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.cloud.Configuration;
import it.cloud.Instance;
import it.cloud.VirtualMachine;
import it.cloud.utils.ssh.Jsch;
import it.cloud.utils.ssh.Sshj;

@SuppressWarnings("deprecation")
public class Ssh {

	protected static final Logger logger = LoggerFactory.getLogger(Ssh.class);

	public static final int SSH_PORT = 22;
	
	private String ip;
	private String user;
	private String password;
	private String key;
	
	public Ssh(String ip, String user, String password, String key) {
		this.ip = ip;
		this.user = user;
		this.password = password;
		this.key = key;
	}
	
	public Ssh(String ip, VirtualMachine vm) {
		this(ip, vm.getParameter("SSH_USER"), vm.getParameter("SSH_PASS"),
				Configuration.getPathToFile(vm.getParameter("KEYPAIR_NAME").concat(".pem")).toString());
	}
	
	public Ssh(Instance inst) {
		this(inst.getIp(), inst.getSshUser(), inst.getSshPassword(), inst.getKey().toString());
	}
	
	public List<String> exec(String... commands) throws Exception {
		List<String> res = new ArrayList<String>();
		for (String command : commands)
			res.addAll(exec(ip, user, password, key, command));
		return res;
	}
	
	public void receiveFile(String lfile, String rfile) throws Exception {
		receiveFile(ip, user, password, key, lfile, rfile);
	}
	
	public void sendFile(String lfile, String rfile) throws Exception {
		sendFile(ip, user, password, key, lfile, rfile);
	}

	public static List<String> exec(String ip, VirtualMachine vm, String command) throws Exception {
		return exec(ip, vm.getParameter("SSH_USER"), vm.getParameter("SSH_PASS"),
				Configuration.getPathToFile(vm.getParameter("KEYPAIR_NAME").concat(".pem")).toString(), command);
	}

	public static List<String> exec(Instance inst, String command) throws Exception {
		return exec(inst.getIp(), inst.getSshUser(), inst.getSshPassword(), inst.getKey().toString(), command);
	}
	
	public static List<String> exec(String ip, String user, String password, String key, String command)
			throws Exception {
		List<String> res;
		
		long init = System.currentTimeMillis();
		
		switch (usedImplementation.getName()) {
		case Sshj.NAME:
			res = Sshj.exec(ip, user, password, key, command);
			break;
		case Jsch.NAME:
			res = Jsch.exec(ip, user, password, key, command);
			break;
		default:
			throw new RuntimeException("Implementation not handled.");	
		}

		long duration = System.currentTimeMillis() - init;
		logger.debug("Executed `{}` on {} in {}", command, ip, Utilities.durationToString(duration));
		return res;
	}

	public static Thread execInBackground(String ip, VirtualMachine vm, String command) throws Exception {
		return execInBackground(ip, vm.getParameter("SSH_USER"), vm.getParameter("SSH_PASS"),
				Configuration.getPathToFile(vm.getParameter("KEYPAIR_NAME").concat(".pem")).toString(), command);
	}

	public static Thread execInBackground(Instance inst, String command) throws Exception {
		return execInBackground(inst.getIp(), inst.getSshUser(), inst.getSshPassword(), inst.getKey().toString(),
				command);
	}

	public static Thread execInBackground(String ip, String user, String password, String key, String command)
			throws Exception {
		final String fip = ip;
		final String fuser = user;
		final String fpassword = password;
		final String fkey = key;
		final String fcommand = command;

		Thread t = new Thread() {
			public void run() {
				try {
					exec(fip, fuser, fpassword, fkey, fcommand);
				} catch (Exception e) {
					logger.error("Error while executing the command.", e);
				}
			}
		};
		t.start();
		return t;
	}

	public static void receiveFile(String ip, VirtualMachine vm, String lfile, String rfile) throws Exception {
		receiveFile(ip, vm.getParameter("SSH_USER"), vm.getParameter("SSH_PASS"),
				Configuration.getPathToFile(vm.getParameter("KEYPAIR_NAME").concat(".pem")).toString(), lfile, rfile);
	}

	public static void receiveFile(Instance inst, String lfile, String rfile) throws Exception {
		receiveFile(inst.getIp(), inst.getSshUser(), inst.getSshPassword(), inst.getKey().toString(), lfile, rfile);
	}

	public static void receiveFile(String ip, String user, String password, String key, String lfile, String rfile)
			throws Exception {
		long init = System.currentTimeMillis();
		
		switch (usedImplementation.getName()) {
		case Sshj.NAME:
			Sshj.receiveFile(ip, user, password, key, lfile, rfile);
			break;
		case Jsch.NAME:
			Jsch.receiveFile(ip, user, password, key, lfile, rfile);
			break;
		default:
			throw new RuntimeException("Implementation not handled.");
		}
		
		long duration = System.currentTimeMillis() - init;
		logger.debug("File `{}` received from {} in {}", rfile, ip, Utilities.durationToString(duration));
	}

	public static void sendFile(String ip, VirtualMachine vm, String lfile, String rfile) throws Exception {
		sendFile(ip, vm.getParameter("SSH_USER"), vm.getParameter("SSH_PASS"),
				Configuration.getPathToFile(vm.getParameter("KEYPAIR_NAME").concat(".pem")).toString(), lfile, rfile);
	}

	public static void sendFile(Instance inst, String lfile, String rfile) throws Exception {
		sendFile(inst.getIp(), inst.getSshUser(), inst.getSshPassword(), inst.getKey().toString(), lfile, rfile);
	}

	public static void sendFile(String ip, String user, String password, String key, String lfile, String rfile)
			throws Exception {
		long init = System.currentTimeMillis();

		switch (usedImplementation.getName()) {
		case Sshj.NAME:
			Sshj.sendFile(ip, user, password, key, lfile, rfile);
			break;
		case Jsch.NAME:
			Jsch.sendFile(ip, user, password, key, lfile, rfile);
			break;
		default:
			throw new RuntimeException("Implementation not handled.");	
		}
		
		long duration = System.currentTimeMillis() - init;
		logger.debug("File `{}` sent to {} in {}", lfile, ip, Utilities.durationToString(duration));
	}
	
	private static Class<? extends Ssh> usedImplementation = Sshj.class;
	
	public static void setImplementation(Class<? extends Ssh> usedImplementation) {
		if (!usedImplementation.getName().equals(Sshj.NAME) && !usedImplementation.getName().equals(Jsch.NAME))
			throw new RuntimeException("Implementation not handled.");
		
		Ssh.usedImplementation = usedImplementation;
		logger.debug("Using {} as the SSH implementation now...", usedImplementation.getName());
	}
	
}
