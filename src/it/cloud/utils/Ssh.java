package it.cloud.utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.cloud.Configuration;
import it.cloud.Instance;
import it.cloud.VirtualMachine;
import it.cloud.utils.ssh.FakeSsh;
import it.cloud.utils.ssh.Sshj;

public abstract class Ssh {

	protected static final Logger logger = LoggerFactory.getLogger(Ssh.class);

	public static final int SSH_PORT = 22;

	protected String ip;
	protected String user;
	protected String password;
	protected String key;

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

	public abstract List<String> exec(String command) throws Exception;

	public abstract void receiveFile(String lfile, String rfile) throws Exception;

	public abstract void sendFile(String lfile, String rfile) throws Exception;

	public static List<String> exec(String ip, VirtualMachine vm, String command) throws Exception {
		return exec(ip, vm.getParameter("SSH_USER"), vm.getParameter("SSH_PASS"),
				Configuration.getPathToFile(vm.getParameter("KEYPAIR_NAME").concat(".pem")).toString(), command);
	}

	public static List<String> exec(Instance inst, String command) throws Exception {
		return exec(inst.getIp(), inst.getSshUser(), inst.getSshPassword(), inst.getKey().toString(), command);
	}

	@SuppressWarnings("unchecked")
	public static List<String> exec(String ip, String user, String password, String key, String command)
			throws Exception {
		List<String> res;

		long init = System.currentTimeMillis();
		
		Constructor<? extends Ssh> c = usedImplementation.getConstructor(String.class, String.class, String.class, String.class);
		Ssh instance = c.newInstance(ip, user, password, key);
		Method m = usedImplementation.getMethod("exec", String.class);
		res = (List<String>) m.invoke(instance, command);

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
		Constructor<? extends Ssh> c = usedImplementation.getConstructor(String.class, String.class, String.class, String.class);
		Ssh instance = c.newInstance(ip, user, password, key);
		Method m = usedImplementation.getMethod("execInBackground", String.class);
		return (Thread) m.invoke(instance, command);
	}

	public Thread execInBackground(String command) throws Exception {
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

	public static void receiveFile(String ip, VirtualMachine vm, String lfile, String rfile) throws Exception {
		receiveFile(ip, vm.getParameter("SSH_USER"), vm.getParameter("SSH_PASS"),
				Configuration.getPathToFile(vm.getParameter("KEYPAIR_NAME").concat(".pem")).toString(), lfile, rfile);
	}

	public static void receiveFile(Instance inst, String lfile, String rfile) throws Exception {
		receiveFile(inst.getIp(), inst.getSshUser(), inst.getSshPassword(), inst.getKey().toString(), lfile, rfile);
	}

	private static void receiveSingleFile(String ip, String user, String password, String key, String lfile, String rfile)
			throws Exception {
		long init = System.currentTimeMillis();
		
		Constructor<? extends Ssh> c = usedImplementation.getConstructor(String.class, String.class, String.class, String.class);
		Ssh instance = c.newInstance(ip, user, password, key);
		Method m = usedImplementation.getMethod("receiveFile", String.class, String.class);
		m.invoke(instance, lfile, rfile);

		long duration = System.currentTimeMillis() - init;
		logger.debug("File `{}` received from {} in {}", rfile, ip, Utilities.durationToString(duration));
	}
	
	public static void receiveFile(String ip, String user, String password, String key, String lfile, String rfile)
			throws Exception {
		if (rfile.contains("*")) {
			String lsFiles = String.format("ls -lLp %s | awk '{ out=$9; for(i=10;i<=NF;i++) {out=out\" \"$i}; print out }' | grep --color=no '^/'", rfile);
			List<String> res = exec(ip, user, password, key, lsFiles);
			for (String s : res) {
				if (s.startsWith("/")) {
					String[] partsRfile = rfile.split("[*]");
					String[] partsLfile = lfile.split("[*]");
					int j = 0, k = 0;
					String actualLfile = partsLfile[0];
					for (int i = 0; i+1 < partsRfile.length; ++i) {
						j = s.indexOf(partsRfile[i], j) + partsRfile[i].length();
						k = s.indexOf(partsRfile[i+1], j);
						String subst = s.substring(j, k);
						actualLfile += subst + partsLfile[i+1];
					}
					receiveSingleFile(ip, user, password, key, actualLfile, s);
				}
			}
		} else {
			receiveSingleFile(ip, user, password, key, lfile, rfile);
		}
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

		Constructor<? extends Ssh> c = usedImplementation.getConstructor(String.class, String.class, String.class, String.class);
		Ssh instance = c.newInstance(ip, user, password, key);
		Method m = usedImplementation.getMethod("sendFile", String.class, String.class);
		m.invoke(instance, lfile, rfile);

		long duration = System.currentTimeMillis() - init;
		logger.debug("File `{}` sent to {} in {}", lfile, ip, Utilities.durationToString(duration));
	}

	protected static Class<? extends Ssh> usedImplementation = Sshj.class;

	public static void setImplementation(Class<? extends Ssh> usedImplementation) {
		Ssh.usedImplementation = usedImplementation;
		logger.debug("Using {} as the SSH implementation now...", usedImplementation.getName());
	}
	
	public static void main(String[] args) throws Exception {
		setImplementation(FakeSsh.class);
		
		Ssh.execInBackground("ip", "user", "password", "key", "command");
	}
	
	public void localSendFile(String lfile, String rfile) throws Exception {
		if (!new File(lfile).exists())
			throw new FileNotFoundException("File " + lfile + " not found!");
		
		if (new File(rfile).exists() && new File(rfile).isDirectory() && !rfile.endsWith(File.separator))
			rfile = rfile + File.separator;
		
		String command = String.format("cp %s %s", lfile, rfile);
		Local.exec(command);
	}
	
	public void localReceiveFile(String lfile, String rfile) throws Exception {
		if (!new File(rfile).exists())
			throw new FileNotFoundException("File " + rfile + " not found!");
		
		if (new File(lfile).exists() && new File(lfile).isDirectory() && !lfile.endsWith(File.separator))
			lfile = lfile + File.separator;
		
		String command = String.format("cp %s %s", rfile, lfile);
		Local.exec(command);
	}
	
	public List<String> localExec(String command) throws Exception {
		return Local.exec(command);
	}

}
