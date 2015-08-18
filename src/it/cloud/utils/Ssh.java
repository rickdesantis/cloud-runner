package it.cloud.utils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.cloud.Configuration;
import it.cloud.Instance;
import it.cloud.VirtualMachine;
import net.schmizz.keepalive.KeepAliveProvider;
import net.schmizz.sshj.DefaultConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.xfer.scp.SCPException;

public class Ssh {

	private static final Logger logger = LoggerFactory.getLogger(Ssh.class);

	public static final int SSH_PORT = 22;

	public static List<String> exec(String ip, VirtualMachine vm, String command) throws Exception {
		return exec(ip, vm.getParameter("SSH_USER"), vm.getParameter("SSH_PASS"),
				Configuration.getPathToFile(vm.getParameter("KEYPAIR_NAME") + ".pem").toString(), command);
	}

	public static List<String> exec(Instance inst, String command) throws Exception {
		return exec(inst.getIp(), inst.getSshUser(), inst.getSshPassword(), inst.getKey().toString(), command);
	}
	
	private static SSHClient getConnectedClient(String ip, String user, String password, String key) throws Exception {
		if (password == null && key == null)
			throw new Exception("You need to provide one among the key and the password to be used.");
		
		DefaultConfig defaultConfig = new DefaultConfig();
        defaultConfig.setKeepAliveProvider(KeepAliveProvider.KEEP_ALIVE);

		final SSHClient ssh = new SSHClient(defaultConfig);
		
		ssh.addHostKeyVerifier(new PromiscuousVerifier());

		ssh.connect(ip, SSH_PORT);
		
		if (key != null) {
			if (!key.endsWith(".pem"))
				key += ".pem";
			Path p = Configuration.getPathToFile(key);
			if (p != null)
				ssh.authPublickey(user, p.toString());
		} else {	
			ssh.authPassword(user, password);
		}
		
		ssh.getConnection().getKeepAlive().setKeepAliveInterval(5);
		
		return ssh;
	}

	public static List<String> exec(String ip, String user, String password, String key, String command)
			throws Exception {
		final List<String> res = new ArrayList<String>();
		
		logger.trace("Executing `{}` on {}...", command, ip);

		final SSHClient ssh = getConnectedClient(ip, user, password, key);
		
		try {
			final Session session = ssh.startSession();
			session.allocateDefaultPTY();
			
			try {
				final Command cmd = session.exec(command);

				Thread in = new Thread() {
					public void run() {
						try (Scanner sc = new Scanner(cmd.getInputStream())) {
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
						try (Scanner sc = new Scanner(cmd.getErrorStream())) {
							while (sc.hasNextLine()) {
								String line = sc.nextLine();
								logger.trace(line);
								res.add(line);
							}
						}
					}
				};
				err.start();

				cmd.join();
				in.join();
				err.join();
				
				logger.trace("Done! Exit status: {}", cmd.getExitStatus());
			} finally {
				session.close();
			}
		} finally {
			ssh.disconnect();
			ssh.close();
		}

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
		logger.trace("Receiving file `{}` on {}...", rfile, ip);

		final SSHClient ssh = getConnectedClient(ip, user, password, key);
		
		try {
			final Session session = ssh.startSession();
			try {
				ssh.newSCPFileTransfer().download(rfile, new FileSystemFile(lfile));
				
				logger.trace("Done!");
			} catch (SCPException e) {
				if (e.getMessage().contains("No such file or directory"))
					logger.warn("No file or directory `{}` found on {}.", rfile, ip);
				else throw e;
			} finally {
				session.close();
			}
		} finally {
			ssh.disconnect();
			ssh.close();
		}
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
		logger.trace("Sending file `{}` on {}...", lfile, ip);

		final SSHClient ssh = getConnectedClient(ip, user, password, key);
		
		try {
			final Session session = ssh.startSession();
			try {
				ssh.newSCPFileTransfer().upload(new FileSystemFile(lfile), rfile);
				
				logger.trace("Done!");
			} finally {
				session.close();
			}
		} finally {
			ssh.disconnect();
			ssh.close();
		}
	}
	
	public static void main(String[] args) throws Exception {
		String cmd1 = "bash test.sh";
		String cmd2 = "bash test2.sh";
		
		System.out.println("======= Ssh =======");
		Ssh.exec("109.231.126.56", "ubuntu", "ubuntu", "/Users/ft/Documents/keys/polimi-review-2014.pem", cmd1);
		Ssh.exec("109.231.126.56", "ubuntu", "ubuntu", "/Users/ft/Documents/keys/polimi-review-2014.pem", cmd2);
		
		System.out.println("======= SshOld =======");
		SshOld.exec("109.231.126.56", "ubuntu", "ubuntu", "/Users/ft/Documents/keys/polimi-review-2014.pem", cmd1);
		SshOld.exec("109.231.126.56", "ubuntu", "ubuntu", "/Users/ft/Documents/keys/polimi-review-2014.pem", cmd2);
	}
	
}
