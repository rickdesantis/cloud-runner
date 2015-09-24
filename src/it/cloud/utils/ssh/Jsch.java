package it.cloud.utils.ssh;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import it.cloud.Instance;
import it.cloud.VirtualMachine;
import it.cloud.utils.Ssh;

public class Jsch extends Ssh {

	public static final String NAME = "it.cloud.utils.ssh.Jsch";

	public Jsch(String ip, String user, String password, String key) {
		super(ip, user, password, key);
	}

	public Jsch(String ip, VirtualMachine vm) {
		super(ip, vm);
	}

	public Jsch(Instance inst) {
		super(inst);
	}

	@Deprecated
	public List<String> execWithoutEnvironment(String command) throws Exception {
		final List<String> res = new ArrayList<String>();

		// creating session with username, server's address and port (22 by
		// default)
		JSch jsch = new JSch();

		jsch.addIdentity(key);

		Session session = jsch.getSession(user, ip, 22);
		session.setPassword(password);

		// disabling of certificate checks
		session.setConfig("StrictHostKeyChecking", "no");
		// creating connection
		session.connect();

		// creating channel in execution mod
		final Channel channel = session.openChannel("exec");
		// sending command which runs bash-script in UploadPath directory
		((ChannelExec) channel).setCommand(command);
		// taking input stream
		channel.setInputStream(null);
		// connecting channel
		channel.connect();

		Thread in = new Thread() {
			public void run() {
				try (Scanner sc = new Scanner(channel.getInputStream())) {
					while (sc.hasNextLine()) {
						String line = sc.nextLine();
						logger.trace(line);
						res.add(line);
					}
				} catch (Exception e) {
					logger.error("Error while considering the input stream.", e);
				}
			}
		};
		in.start();

		Thread err = new Thread() {
			public void run() {
				try (Scanner sc = new Scanner(((ChannelExec) channel).getErrStream())) {
					while (sc.hasNextLine()) {
						String line = sc.nextLine();
						logger.trace(line);
						res.add(line);
					}
				} catch (Exception e) {
					logger.error("Error while considering the error stream.", e);
				}
			}
		};
		err.start();

		in.join();
		err.join();
		if (channel.isClosed())
			res.add("exit-status: " + channel.getExitStatus());

		// closing connection
		channel.disconnect();
		session.disconnect();

		return res;
	}

	public static final String FINISHED_FLAG = "TERMINATO_TUTTO_TUTTO";

	public List<String> exec(String command) throws Exception {
		List<String> res = new ArrayList<String>();

		// creating session with username, server's address and port (22 by
		// default)
		JSch jsch = new JSch();

		jsch.addIdentity(key);

		Session session = jsch.getSession(user, ip, 22);
		session.setPassword(password);

		// disabling of certificate checks
		session.setConfig("StrictHostKeyChecking", "no");
		// creating connection
		session.connect();

		// creating channel in shell mode
		ChannelShell channel = (ChannelShell) session.openChannel("shell");
		// connecting channel
		channel.connect();

		channel.setPtySize(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

		try (PrintStream out = new PrintStream(channel.getOutputStream());
				Scanner in = new Scanner(channel.getInputStream())) {
			out.println("echo " + FINISHED_FLAG);
			out.flush();

			while (in.hasNextLine()) {
				String line = in.nextLine();
				if (line.equals(FINISHED_FLAG))
					break;
			}

			out.println(command);
			out.flush();

			in.nextLine();

			try {
				Thread.sleep(100);
			} catch (Exception e) {
			}

			out.println("echo " + FINISHED_FLAG);
			out.flush();

			while (in.hasNextLine()) {
				String line = in.nextLine();
				if (line.equals(FINISHED_FLAG))
					break;
				if (line.contains(FINISHED_FLAG))
					continue;
				logger.trace(line);
				res.add(line);
			}
		}

		// closing connection
		channel.disconnect();
		session.disconnect();

		return res;
	}

	public void receiveFile(String lfile, String rfile) throws Exception {
		FileOutputStream fos = null;
		try {
			// creating session with username, server's address and port (22 by
			// default)
			JSch jsch = new JSch();

			jsch.addIdentity(key);

			Session session = jsch.getSession(user, ip, 22);
			session.setPassword(password);

			String prefix = null;
			if (new File(lfile).isDirectory()) {
				prefix = lfile + File.separator;
			}
			// session.setUserInfo(ui);
			// disabling of certificate checks
			session.setConfig("StrictHostKeyChecking", "no");
			session.connect();
			// exec 'scp -f rfile' remotely
			String command = "scp -f " + rfile;
			Channel channel = session.openChannel("exec");
			((ChannelExec) channel).setCommand(command);
			// get I/O streams for remote scp
			OutputStream out = channel.getOutputStream();
			InputStream in = channel.getInputStream();

			channel.connect();

			byte[] buf = new byte[1024];

			buf[0] = 0;
			out.write(buf, 0, 1);
			out.flush();
			// reading channel
			while (true) {
				int c = checkAck(in);
				if (c != 'C') {
					break;
				}

				in.read(buf, 0, 5);

				long filesize = 0L;
				while (true) {
					if (in.read(buf, 0, 1) < 0) {
						break;
					}
					if (buf[0] == ' ')
						break;
					filesize = filesize * 10L + (long) (buf[0] - '0');
				}

				String file = null;
				for (int i = 0;; i++) {
					in.read(buf, i, 1);
					if (buf[i] == (byte) 0x0a) {
						file = new String(buf, 0, i);
						break;
					}
				}

				buf[0] = 0;
				out.write(buf, 0, 1);
				out.flush();
				fos = new FileOutputStream(prefix == null ? lfile : prefix + file);
				int foo;
				while (true) {
					if (buf.length < filesize)
						foo = buf.length;
					else
						foo = (int) filesize;
					foo = in.read(buf, 0, foo);
					if (foo < 0) {
						break;
					}
					fos.write(buf, 0, foo);
					filesize -= foo;
					if (filesize == 0L)
						break;
				}
				fos.close();
				fos = null;

				if (checkAck(in) != 0) {
					System.exit(0);
				}

				buf[0] = 0;
				out.write(buf, 0, 1);
				out.flush();
			}

			session.disconnect();
		} catch (Exception e) {
			logger.error("Error while receiving the file.", e);
			try {
				if (fos != null)
					fos.close();
			} catch (Exception ee) {
			}
		}
	}

	public void sendFile(String lfile, String rfile) throws Exception {
		FileInputStream fis = null;
		try {
			// creating session with username, server's address and port (22 by
			// default)
			JSch jsch = new JSch();

			jsch.addIdentity(key);

			Session session = jsch.getSession(user, ip, 22);
			session.setPassword(password);

			// disabling of certificate checks
			session.setConfig("StrictHostKeyChecking", "no");
			// creating connection
			session.connect();

			boolean ptimestamp = true;
			// exec 'scp -t rfile' remotely
			String command = "scp " + (ptimestamp ? "-p" : "") + " -t " + rfile;
			Channel channel = session.openChannel("exec");
			((ChannelExec) channel).setCommand(command);
			// get I/O streams for remote scp
			OutputStream out = channel.getOutputStream();
			InputStream in = channel.getInputStream();
			// connecting channel
			channel.connect();

			if (checkAck(in) != 0) {
				System.exit(0);
			}

			File _lfile = new File(lfile);

			if (ptimestamp) {
				command = "T" + (_lfile.lastModified() / 1000) + " 0";
				command += (" " + (_lfile.lastModified() / 1000) + " 0\n");
				out.write(command.getBytes());
				out.flush();
				if (checkAck(in) != 0) {
					System.exit(0);
				}
			}
			// send "C0644 filesize filename", where filename should not include
			// '/'
			long filesize = _lfile.length();
			command = "C0644 " + filesize + " ";
			if (lfile.lastIndexOf('/') > 0) {
				command += lfile.substring(lfile.lastIndexOf('/') + 1);
			} else {
				command += lfile;
			}
			command += "\n";
			out.write(command.getBytes());
			out.flush();
			if (checkAck(in) != 0) {
				System.exit(0);
			}
			// send a content of lfile
			fis = new FileInputStream(lfile);
			byte[] buf = new byte[1024];
			while (true) {
				int len = fis.read(buf, 0, buf.length);
				if (len <= 0)
					break;
				out.write(buf, 0, len);
			}
			fis.close();
			fis = null;
			buf[0] = 0;
			out.write(buf, 0, 1);
			out.flush();
			if (checkAck(in) != 0) {
				System.exit(0);
			}
			out.close();

			channel.disconnect();
			session.disconnect();

		} catch (Exception e) {
			logger.error("Error while sending the file.", e);
			try {
				if (fis != null)
					fis.close();
			} catch (Exception ee) {
			}
		}
	}

	private static int checkAck(InputStream in) throws IOException {
		int b = in.read();
		if (b == 0)
			return b;
		if (b == -1)
			return b;

		if (b == 1 || b == 2) {
			StringBuffer sb = new StringBuffer();
			int c;
			do {
				c = in.read();
				sb.append((char) c);
			} while (c != '\n');
			if (b == 1) { // error
				logger.error("Error: " + sb.toString());
			}
			if (b == 2) { // fatal error
				logger.error("Fatal error: " + sb.toString());
			}
		}
		return b;
	}
	
}