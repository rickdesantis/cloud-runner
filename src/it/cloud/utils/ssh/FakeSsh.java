package it.cloud.utils.ssh;

import java.util.List;

import it.cloud.Instance;
import it.cloud.VirtualMachine;
import it.cloud.utils.Ssh;

public class FakeSsh extends Ssh {

	public FakeSsh(String ip, String user, String password, String key) {
		super(ip, user, password, key);
	}

	public FakeSsh(String ip, VirtualMachine vm) {
		super(ip, vm);
	}

	public FakeSsh(Instance inst) {
		super(inst);
	}

	@Override
	public List<String> exec(String command) throws Exception {
		logger.info("exec(`{}`)", command);
		return null;
	}

	@Override
	public void receiveFile(String lfile, String rfile) throws Exception {
		logger.info("receiveFile(`{}`, `{}`)", lfile, rfile);
	}

	@Override
	public void sendFile(String lfile, String rfile) throws Exception {
		logger.info("sendFile(`{}`, `{}`)", lfile, rfile);
	}

}
