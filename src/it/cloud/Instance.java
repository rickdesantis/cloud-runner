package it.cloud;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.cloud.VirtualMachine;
import it.cloud.amazon.Configuration;
import it.cloud.utils.Ssh;

public abstract class Instance {
	
	protected static final Logger logger = LoggerFactory.getLogger(Instance.class);
	
	public String id;
	
	protected VirtualMachine vm;
	
	protected String ip;
	
	protected Instance(VirtualMachine vm, String id) {
		this.id = id;
		this.vm = vm;

		ip = null;
	}

	public String getParameter(String name) {
		return vm.getParameter(name);
	}

	public String getName() {
		return vm.name;
	}

	public Path getKey() {
		return Configuration.getPathToFile(vm.keyName + ".pem");
	}

	public String getSshUser() {
		return vm.sshUser;
	}

	public String getSshPassword() {
		return vm.sshPassword;
	}

	public List<String> exec(String cmd) throws Exception {
		return Ssh.exec(this, cmd);
	}

	public void receiveFile(String lfile, String rfile) throws Exception {
		Ssh.receiveFile(this, lfile, rfile);
	}

	public void sendFile(String lfile, String rfile) throws Exception {
		Ssh.sendFile(this, lfile, rfile);
	}
	
	public String getIp() {
		return ip;
	}
	
	public List<String> execStarter() throws Exception {
		String cmd = getParameter("STARTER");
		if (cmd != null)
			return exec(cmd);
		return new ArrayList<String>();
	}
	public List<String> execStopper() throws Exception {
		String cmd = getParameter("STOPPER");
		if (cmd != null)
			return exec(cmd);
		return new ArrayList<String>();
	}
	public List<String> execUpdater() throws Exception {
		String cmd = getParameter("UPDATER");
		if (cmd != null)
			return exec(cmd);
		return new ArrayList<String>();
	}
	public List<String> execDownloader() throws Exception {
		String cmd = getParameter("DOWNLOADER");
		if (cmd != null)
			return exec(cmd);
		return new ArrayList<String>();
	}
	public List<String> execInstaller() throws Exception {
		String cmd = getParameter("INSTALLER");
		if (cmd != null)
			return exec(cmd);
		return new ArrayList<String>();
	}
	
	public abstract void terminate();
	public abstract void reboot();
	
	public abstract boolean waitUntilRunning(boolean initializedIsEnough);
	
	public static final long TIMEOUT = 60000;
	
	public boolean waitUntilSshAvailable() {
		waitUntilRunning(false);
		
		long init = System.currentTimeMillis();
		long end = init;
		boolean done = false;
		while (!done && ((end - init) <= TIMEOUT)) {
			try {
				exec("echo foo > /dev/null");
				done = true;
			} catch (Exception e) {
				try {
					Thread.sleep(1000);
				} catch (Exception e1) { }
			}
			end = System.currentTimeMillis();
		}
		return done;
	}
}
