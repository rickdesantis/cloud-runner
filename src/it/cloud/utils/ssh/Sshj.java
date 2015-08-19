package it.cloud.utils.ssh;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import it.cloud.Configuration;
import it.cloud.utils.Ssh;
import net.schmizz.keepalive.KeepAliveProvider;
import net.schmizz.sshj.DefaultConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.xfer.FileSystemFile;
import net.schmizz.sshj.xfer.scp.SCPException;

public class Sshj extends Ssh {
	
	public static final String NAME = "it.cloud.utils.ssh.Sshj"; 
	
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
		
		final SSHClient ssh = getConnectedClient(ip, user, password, key);
		
		try {
			final Session session = ssh.startSession();
//			session.allocateDefaultPTY();
			
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
				
				res.add("exit-status: " + cmd.getExitStatus());
			} finally {
				session.close();
			}
		} finally {
			ssh.disconnect();
			ssh.close();
		}

		return res;
	}
	
	public static void receiveFile(String ip, String user, String password, String key, String lfile, String rfile)
			throws Exception {
		final SSHClient ssh = getConnectedClient(ip, user, password, key);
		
		try {
			final Session session = ssh.startSession();
			try {
				ssh.newSCPFileTransfer().download(rfile, new FileSystemFile(lfile));
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
	
	public static void sendFile(String ip, String user, String password, String key, String lfile, String rfile)
			throws Exception {
		final SSHClient ssh = getConnectedClient(ip, user, password, key);
		
		try {
			final Session session = ssh.startSession();
			try {
				ssh.newSCPFileTransfer().upload(new FileSystemFile(lfile), rfile);
			} finally {
				session.close();
			}
		} finally {
			ssh.disconnect();
			ssh.close();
		}
	}
}