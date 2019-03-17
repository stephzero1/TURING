import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;

/**
 * Thread che gestisce l'invio e la ricezione di messaggi
 * utilizzando Multicast UDP.
 * Viene utilizzato dal client per comunicare con altri client
 * che nel sistema turing stanno editando lo stesso file.
 * La visualizzazzione dei messaggi è asincrona e su richiesta dei client.
 * 
 * @author Stefano Spadola 534919 
 */

public class ChatThread implements Runnable{

	private MulticastSocket socket;
	private InetAddress address;
	private byte[] buffer = new byte[8192];
	private ArrayList<String> chathistory;
	
	public ChatThread(String address, int port, ArrayList<String> chathistory) throws IOException {
		this.address = InetAddress.getByName(address.substring(1));
		this.socket=new MulticastSocket(port);
		this.chathistory=chathistory;
	}
	
	@Override
	public void run() {
		try {
			socket.joinGroup(this.address);
			while(true) {
				DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
				socket.receive(packet);
				String s = new String(packet.getData());
				chathistory.add(s);				
			}		
		} catch (IOException e) {
			System.out.println("Oh something bad...");
			e.printStackTrace();
		}	
	}
	
	
	
	
	

}
