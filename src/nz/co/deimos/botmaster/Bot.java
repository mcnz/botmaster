package nz.co.deimos.botmaster;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Bot {

	private final BotMaster plugin;
	private final String botname;
	private final String username;
	private final String authenticator;
	private final List<String> onmotd;
	private final List<String> onping;
	private boolean botmaster = false;
	private Set<String> channels = new HashSet<String>();
	private SocketChannel socket;
	private ByteBuffer buffer;
	private String input = new String();
	
	public Bot(BotMaster p, String b, String u, String a, List<String> m, List<String> o) {
		plugin = p;
		botname = b;
		username = u;
		authenticator = a;
		onmotd = m;
		onping = o;
	}
	
	public String getBotName() {
		return botname;
	}

	public void addChannel(String chan) {
		channels.add(chan);
	}
	
	public void setMaster() {
		botmaster = true;
	}
	
	public void addOnPingCalls(List<String> l) {
		for (String s : l) {
			onping.add(s);
		}
	}
	
	public void addOnMotdCalls(List<String> l) {
		for (String s : l) {
			onmotd.add(s);
		}
	}
	
	public SocketChannel createSocket(String host, int port) throws IOException {
		socket = SocketChannel.open();
		socket.configureBlocking(true);
		socket.connect(new InetSocketAddress(host, port));
		socket.configureBlocking(false);
		buffer = ByteBuffer.allocate(4096);
		buffer.clear();
		commence();
		return socket;
	}
	
	private void commence() throws IOException {
		put("NICK " + botname + "\r\n");
		put("USER " + username + " 0 * :" + botname + "\r\n");
		send();
	}
	
	private void put(String s) {
		buffer.put(s.getBytes());
	}
	
	private void send() throws IOException {
		buffer.flip();
		socket.write(buffer);
		buffer.clear();
	}
	
	public void sendPrivmsg(String c, String s) {
		try {
			put("PRIVMSG #" + c + " :" + s + "\r\n");
			send();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void onRecv(String s) throws IOException {
		String m;
		String[] p;
		int n;
		
		input += s;
		
		n = input.indexOf('\n');
		while (n > 0) {
			
			// update loop parameters
			m = input.substring(0, n).trim();
			input = input.substring(n+1);
			n = input.indexOf('\n');
			
			//plugin.logger.info("RECV: " + m);
			p = m.split(" ");
		
			// keep alive
			if (p[0].equals("PING")) {
				put("PONG " + p[1] + "\r\n");
				for (String l : onping) {
					put(l + "\r\n");
				}
			
			// onMotd
			} else if (p[1].equals("376") || p[1].equals("422")) {
				for (String l : onmotd) {
					put(l + "\r\n");
				}
				for (String c : channels) {
					put("JOIN #" + c + "\r\n");
				}
			
			// private message
			} else if (p[1].equals("PRIVMSG")) {
				
				if (botmaster) {
					String sender = m.substring(1, m.indexOf('!'));
					String message = m.substring(m.indexOf(':', 1) + 1);
					String channel = p[2].substring(1);
					if (channels.contains(channel) && !plugin.botnameExists(sender)) {
						plugin.onPrivmsg(channel, sender, message);
					
					} else if (sender.equalsIgnoreCase(authenticator) && p[3].equals(":1")) {
						// we got MineBot's session response
						plugin.clearIRCStaff();
						for (short i = 4; i < p.length; i ++) {
							plugin.addIRCStaff(p[i].substring(p[i].indexOf(':') + 1));
						}
					}
				}
				
			}
		}
		
		if (buffer.hasRemaining()) send();
	}

	public void kill() {
		put("QUIT :MCNZ BotMaster <plugins@deimos.co.nz>\r\n");
		try {
			send();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
