package it.cloud.utils;

import it.cloud.Instance;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

public class Ssh {
	
	public static class Connection {
		public Connection(Instance inst) throws Exception {
			this(inst.getIp(), inst.getSshUser(), inst.getSshPassword(), inst.getKey());
		}
		
		private JSch jsch;
		
		private String ip;
		private String username;
		private String password;
		@SuppressWarnings("unused")
		private Path key;
		
		public Connection(String ip, String username, String password, Path key) throws Exception {
			this.ip = ip;
			this.username = username;
			this.password = password;
			this.key = key;
			
			// creating session with username, server's address and port (22 by
			// default)
			jsch = new JSch();
			
			jsch.addIdentity(key.toString());
		}
		
		public Channel openChannel(String command) throws Exception {
			Session session = jsch.getSession(username, ip, 22);
			session.setPassword(password);

			// disabling of certificate checks
			session.setConfig("StrictHostKeyChecking", "no");
			// creating connection
			session.connect();
			
			// creating channel in execution mod
			com.jcraft.jsch.Channel channel = session.openChannel("exec");
			// sending command which runs bash-script in UploadPath directory
			((ChannelExec) channel).setCommand(command);
			// taking input stream
			channel.setInputStream(null);
			((ChannelExec) channel).setErrStream(System.err);
			InputStream in = channel.getInputStream();
			OutputStream out = channel.getOutputStream();
			// connecting channel
			channel.connect();
			
			return new Channel(session, channel, in, out);
		}
		
		public class Channel implements AutoCloseable {
			private Session session;
			private com.jcraft.jsch.Channel channel;
			private InputStream in;
			private OutputStream out;
			
			private Channel(Session session, com.jcraft.jsch.Channel channel, InputStream in, OutputStream out) {
				this.session = session;
				this.channel = channel;
				this.in = in;
				this.out = out;
			}
			
			public InputStream getInputStream() {
				return in;
			}
			
			public OutputStream getOutputStream() {
				return out;
			}
			
			public boolean isClosed() {
				return channel.isClosed();
			}
			
			public int getExitStatus() {
				return channel.getExitStatus();
			}
			
			public void close() throws Exception {
				in.close();
				out.close();
				channel.disconnect();
				session.disconnect();
			}
		}
	}

	@Deprecated
	public static List<String> execOld(Instance inst, String command) throws Exception {
		List<String> res = new ArrayList<String>();
		
		// creating session with username, server's address and port (22 by
		// default)
		JSch jsch = new JSch();
		
		jsch.addIdentity(inst.getKey().toString());
		
		Session session = jsch.getSession(inst.getSshUser(), inst.getIp(), 22);
		session.setPassword(inst.getSshPassword());

		// disabling of certificate checks
		session.setConfig("StrictHostKeyChecking", "no");
		// creating connection
		session.connect();

		// creating channel in execution mod
		Channel channel = session.openChannel("exec");
		// sending command which runs bash-script in UploadPath directory
		((ChannelExec) channel).setCommand(command);
		// taking input stream
		channel.setInputStream(null);
		((ChannelExec) channel).setErrStream(System.err);
		InputStream in = channel.getInputStream();
		// connecting channel
		channel.connect();
		// read buffer
		byte[] tmp = new byte[1024];

		// reading channel while server responses smth or until it does not
		// close connection
		while (true) {
			while (in.available() > 0) {
				int i = in.read(tmp, 0, 1024);
				if (i < 0)
					break;
				res.add(new String(tmp, 0, i));
			}
			if (channel.isClosed()) {
				res.add("exit-status: " + channel.getExitStatus());
				break;
			}
			try {
				Thread.sleep(1000);
			} catch (Exception ee) {
			}
		}
		// closing connection
		channel.disconnect();
		session.disconnect();

		return res;
	}
	
	public static List<String> exec(Instance inst, String command) throws Exception {
		List<String> res = new ArrayList<String>();
		
		Connection conn = new Connection(inst);
		
		try (it.cloud.utils.Ssh.Connection.Channel channel = conn.openChannel(command)) {
			InputStream in = channel.getInputStream();
			
			// read buffer
			byte[] tmp = new byte[1024];

			// reading channel while server responses smth or until it does not
			// close connection
			while (true) {
				while (in.available() > 0) {
					int i = in.read(tmp, 0, 1024);
					if (i < 0)
						break;
					res.add(new String(tmp, 0, i));
				}
				if (channel.isClosed()) {
					res.add("exit-status: " + channel.getExitStatus());
					break;
				}
				try {
					Thread.sleep(1000);
				} catch (Exception ee) { }
			}
		}

		return res;
	}
	
	@Deprecated
	public static void receiveFileOld(Instance inst, String lfile, String rfile) throws Exception {
		FileOutputStream fos = null;
		try {
			// creating session with username, server's address and port (22 by
			// default)
			JSch jsch = new JSch();
			
			jsch.addIdentity(inst.getKey().toString());
			
			Session session = jsch.getSession(inst.getSshUser(), inst.getIp(), 22);
			session.setPassword(inst.getSshPassword());

			String prefix = null;
			if (new File(lfile).isDirectory()) {
				prefix = lfile + File.separator;
			}
//			session.setUserInfo(ui);
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
				fos = new FileOutputStream(prefix == null ? lfile : prefix
						+ file);
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
			e.printStackTrace();
			try {
				if (fos != null)
					fos.close();
			} catch (Exception ee) {
			}
		}
	}
	
	public static void receiveFile(Instance inst, String lfile, String rfile) throws Exception {
		Connection conn = new Connection(inst);
		String command = "scp -f " + rfile;
		
		try (it.cloud.utils.Ssh.Connection.Channel channel = conn.openChannel(command)) {
			OutputStream out = channel.getOutputStream();
			InputStream in = channel.getInputStream();
			
			String prefix = null;
			if (new File(lfile).isDirectory()) {
				prefix = lfile + File.separator;
			}

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
				
				try (FileOutputStream fos = new FileOutputStream(prefix == null ? lfile : prefix + file)) {
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
				}
				

				if (checkAck(in) != 0) {
					System.exit(0);
				}

				buf[0] = 0;
				out.write(buf, 0, 1);
				out.flush();
			}
		}
	}
	
	@Deprecated
	public static void sendFileOld(Instance inst, String lfile, String rfile) throws Exception {
		FileInputStream fis = null;
		try {
			// creating session with username, server's address and port (22 by
			// default)
			JSch jsch = new JSch();
			
			jsch.addIdentity(inst.getKey().toString());
			
			Session session = jsch.getSession(inst.getSshUser(), inst.getIp(), 22);
			session.setPassword(inst.getSshPassword());

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
			e.printStackTrace();
			try {
				if (fis != null)
					fis.close();
			} catch (Exception ee) {
			}
		}
	}
	
	public static void sendFile(Instance inst, String lfile, String rfile) throws Exception {
		Connection conn = new Connection(inst);
		
		boolean ptimestamp = true;
		// exec 'scp -t rfile' remotely
		String command = "scp " + (ptimestamp ? "-p" : "") + " -t " + rfile;
		
		try (it.cloud.utils.Ssh.Connection.Channel channel = conn.openChannel(command)) {
			OutputStream out = channel.getOutputStream();
			InputStream in = channel.getInputStream();

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
			byte[] buf = new byte[1024];
			try (FileInputStream fis = new FileInputStream(lfile)) {
				while (true) {
					int len = fis.read(buf, 0, buf.length);
					if (len <= 0)
						break;
					out.write(buf, 0, len);
				}
			}
			buf[0] = 0;
			out.write(buf, 0, 1);
			out.flush();
			if (checkAck(in) != 0) {
				System.exit(0);
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
				System.out.print(sb.toString());
			}
			if (b == 2) { // fatal error
				System.out.print(sb.toString());
			}
		}
		return b;
	}

}
