package nz.co.deimos.botmaster;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class IRCSelector implements Runnable {
	
	private final BotMaster plugin;
	private final Selector selector;
	private final Map<SocketChannel, Bot> socketchannelmap = new HashMap<SocketChannel, Bot>();
	private ByteBuffer buffer = ByteBuffer.allocate(1024);
	private boolean alive;
	
	
	public IRCSelector(BotMaster b) throws IOException {
		plugin = b;
		selector = Selector.open();
	}

	public void run() {
		try {
			Iterator<SelectionKey> it;
			SelectionKey sk;
			SocketChannel sc;
			
			alive = true;
			
			while (alive) {
				// amazing what can be achieved in 0.5s
				selector.select(500);
				
				it = selector.selectedKeys().iterator();
				
				while (it.hasNext()) {
					sk = it.next();
					sc = (SocketChannel) sk.channel();
					processSelectionKey(sk, sc);
					it.remove();
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			plugin.logger.severe("Thread IRCSelector died horribly");
			return;
		}
		
		plugin.logger.info("Thread IRCSelector exited cleanly!");
		
	}
	
	private void processSelectionKey(SelectionKey s, SocketChannel c) throws IOException {
		if (s.isValid()) {
			if (s.isReadable()) {
				int bytesRead = c.read(buffer);
				if (bytesRead < 0) {
					plugin.logger.info("Closing connection: " + s);
					s.cancel();
					s.channel().close();
				} else {
					buffer.rewind();
					
					byte[] bs = new byte[bytesRead];
					buffer.get(bs);
					socketchannelmap.get(c).onRecv(new String(bs));
					
					buffer.clear();
				}
			}
		}
	}

	public void registerSocket(SocketChannel socket, Bot bot) throws ClosedChannelException {
		socket.register(selector, SelectionKey.OP_READ);
		socketchannelmap.put(socket, bot);
	}
	
	public void kill() {
		alive = false;
	}

}
