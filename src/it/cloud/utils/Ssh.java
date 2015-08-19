package it.cloud.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.cloud.Configuration;
import it.cloud.Instance;
import it.cloud.VirtualMachine;
import it.cloud.utils.ssh.Jsch;
import it.cloud.utils.ssh.Sshj;

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
				Configuration.getPathToFile(vm.getParameter("KEYPAIR_NAME") + ".pem").toString());
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
				Configuration.getPathToFile(vm.getParameter("KEYPAIR_NAME") + ".pem").toString(), command);
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
		logger.trace("Executed `{}` on {} in {}", command, ip, durationToString(duration));
		return res;
	}

	public static Thread execInBackground(String ip, VirtualMachine vm, String command) throws Exception {
		return execInBackground(ip, vm.getParameter("SSH_USER"), vm.getParameter("SSH_PASS"),
				Configuration.getPathToFile(vm.getParameter("KEYPAIR_NAME") + ".pem").toString(), command);
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
				Configuration.getPathToFile(vm.getParameter("KEYPAIR_NAME") + ".pem").toString(), lfile, rfile);
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
		logger.trace("File `{}` received from {} in {}", rfile, ip, durationToString(duration));
	}

	public static void sendFile(String ip, VirtualMachine vm, String lfile, String rfile) throws Exception {
		sendFile(ip, vm.getParameter("SSH_USER"), vm.getParameter("SSH_PASS"),
				Configuration.getPathToFile(vm.getParameter("KEYPAIR_NAME") + ".pem").toString(), lfile, rfile);
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
		logger.trace("File `{}` sent to {} in {}", lfile, ip, durationToString(duration));
	}
	
	public static String durationToString(long duration) {
		String actualDuration = "";
		{
			int res = (int) TimeUnit.MILLISECONDS.toSeconds(duration);
			if (res > 60 * 60) {
				actualDuration += (res / (60 * 60)) + " h ";
				res = res % (60 * 60);
			}
			if (res > 60) {
				actualDuration += (res / 60) + " m ";
				res = res % 60;
			}
			actualDuration += res + " s";
		}


		return actualDuration;
	}
	
	private static Class<? extends Ssh> usedImplementation = Sshj.class;
	
	public static void setImplementation(Class<? extends Ssh> usedImplementation) {
		if (!usedImplementation.getName().equals(Sshj.NAME) && !usedImplementation.getName().equals(Jsch.NAME))
			throw new RuntimeException("Implementation not handled.");
		
		Ssh.usedImplementation = usedImplementation;
		logger.trace("Using {} as the SSH implementation now...", usedImplementation.getName());
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void main(String[] args) throws Exception {
		String[] cmds = new String[] {
			"echo \"test.sh: `bash test.sh`\" && echo \"test2.sh: `bash test2.sh`\""
//			"bash /home/ubuntu/CloudMLDaemon -port 9000",
//			"ps aux | grep loud",
//			"sleep 180",
//			"bash /home/ubuntu/CloudMLDaemon -port 9000 -stop",
//			"ps aux | grep loud"
		};
		
		Class[] imps = new Class[] {
			Sshj.class //, Jsch.class
		};
		
		Ssh ssh = new Ssh("109.231.126.56", "ubuntu", "ubuntu", "/Users/ft/Documents/keys/polimi-review-2014.pem");
//		Ssh ssh = new Ssh("52.18.154.110", "ubuntu", "ubuntu", "/Users/ft/Documents/keys/desantis-ireland.pem");
		
		for (Class imp : imps) {
			Ssh.setImplementation(imp);
			for (String cmd : cmds) {
				ssh.exec(cmd);
				Thread.sleep(5000);
			}
			logger.trace("============");
		}
		
	}
	
}
