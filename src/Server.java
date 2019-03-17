import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.*;

/**
 * Main principale del server che crea tutte le strutture dati
 * per il corretto funzionamento di Turing.
 * Implementa il servizio per la registrazione via RMI, e fa da
 * selettore per le connessioni in entrata, rendendole dedicate
 * attraverso il lancio di un Thread che gestisce ogni client
 * in maniera autonoma e coordinata con gli altri gestori, il tutto
 * sorvegliato da un newFixedThreadPool.
 * 
 * @author Stefano Spadola 534919 
 */

public class Server {
	
	private static final int PORT = 6666;
	private static InetAddress address = null;

	public static void main(String[] args) {
		
		//Fase di bootstrap
		bootStrap();
		
		/**
		 * Si crea il servizio di registrazione con RMI
		 */
		try {
			SubscribeInterface stub = (SubscribeInterface) UnicastRemoteObject.exportObject(UsersDB.getIstance(), 0);
			LocateRegistry.createRegistry(SubscribeInterface.RMIPORT);
			Registry r = LocateRegistry.getRegistry(SubscribeInterface.RMIPORT);
			r.rebind(SubscribeInterface.SERVICE, stub);
			System.out.println("Servizio di registrazione Online");
			System.out.println("");
		} catch (RemoteException e) {
			e.printStackTrace();
		}
		
		/**
		 * Si lancia la procedura che fa da listener per le connessioni TCP
		 * via SocketChannel in entrata, è la parte princiapale del Server.
		 */
		try {
			tcpDeamon();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		//shutDown();
	}
	
	private static void bootStrap() {		
		/*Avvio del server*/
		System.out.println("Avvio Server...");
		
		/* 1) Si costruiscono le strutture per gli utenti e i file(forzo i singleton)*/
		UsersDB.getIstance();
		FilesDB.getIstance();
	}
	
	//private static void shutDown() {}
	
	/**
	 * Metodo che resta in ascolto per nuove connessioni.
	 * Una volta instaurata una nuova connessione, gli si
	 * dedica un Thread responsabile a soddisfare tutte le richieste
	 * del client. Ogni thread viene associato ad un FixedThreadPool. 
	 */
	private static void tcpDeamon() throws IOException {
		ExecutorService ex = null;
		try(ServerSocketChannel server = ServerSocketChannel.open()){
			server.bind(new InetSocketAddress(InetAddress.getLocalHost(),PORT));
			ex = Executors.newFixedThreadPool(100);
			//Si rimane in ascolto per nuove connessioni (manca un exit point)
			while(true) {
				try {
					SocketChannel client=server.accept();
					client.configureBlocking(true);
					ClientHandler ch = new ClientHandler(client);
					ex.execute(ch);
				}catch(IOException e ) {
					e.printStackTrace();
					System.out.println("#SERVER ERROR: Impossibile accetare Client");
				}
			}			
		}catch(IOException e ) {
			System.out.println("#SERVER ERROR: Impossibile avviare il Server");
			System.out.println("#			   Ending Server...");
		}		
	}
	
	/**
	 * Funzione che genera indirizzi Multicast UDP da assegnare ai file.
	 * Ogni indirizzo viene generato in modo sequenziale, e il metodo viene accesso
	 * in maniera sincornizzata per eviare situazioni di inconsistenza.
	 * WARNING: L'attuale implementazione non permette di assegnare più di
	 * 16581375 indirizzi multicast UDP in una rete locale.
	 * (Andrebbe implementato un riassegnamento degli indirizzi non utilizzati. 
	 */	
	public static synchronized InetAddress getFreeInetAddress() {	
		
		try {
			if(address==null) address=InetAddress.getByName("239.0.0.0");
			else {
				String a = address.getHostAddress();
				System.out.println(a);
				String splitted[] = a.split("\\.");
				Integer pt1=Integer.parseInt(splitted[0]);
				Integer pt2=Integer.parseInt(splitted[1]);
				Integer pt3=Integer.parseInt(splitted[2]);
				Integer pt4=Integer.parseInt(splitted[3]);
				
				if((pt4=(pt4+1)%256)==0) {
					if((pt3=(pt3+1)%256)==0) {
						if((pt2=(pt2+1)%256)==0) {
							if((pt1=(pt1+1)%256)==240)
								return null;
						}
					}
				}

				address=InetAddress.getByName(pt1+"."+pt2+"."+pt3+"."+pt4);
				System.out.println(address);
			}
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
		return address;
	}

}
