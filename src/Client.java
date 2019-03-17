import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.OpenOption;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.rmi.Remote;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Scanner;

/**
 * Applicazione Client-side che permette di interfacciare l'utente
 * con il servizio TURING.
 * Il programma esegue un pre parsing delle query lanciate dagli utenti
 * consentendo di scartarne quelle malformate così da non caricare il server
 * con messaggi inutili.
 * Implementa funzioni di download e upload dei file, e una chat multicast udp
 * senza l'utilizzo di un entità centralizzata quale il server, che servirà
 * solo da "tracker" indicando l'indirizzo condiviso utilizzato per comunicare
 * con gli altri client.
 * <p>
 * Il funzionamento principale è dato dal meccanismo di Richiesta - Risposta
 * con il Server, il quale risponderà con vari messaggi di successo o di errore
 * notificati all'utente mediante una CLI.
 * 
 * @author Stefano Spadola 534919 
 */

public class Client {
	
	/*Porte per la comunicazione*/
	private static final int PORT = 6666;
	private static final int CHAT_PORT = 9899;
	/*Scambio messaggi*/
	private static SocketChannel socket;
	private static PrintWriter writer;
	private static BufferedReader reader;
	/*Variabili di sessione*/
	private static int retry=0;
	private static boolean allowed=false;
	private static boolean editmode=false;
	private static boolean exit=false;
	private static String name=null;
	private static String fileinedit=null;
	private static int sectioninedit;
	/*Risposte e codice errore*/
	private static final int SYNTAX_ERROR=-9;
	private static final int CONCURRENCY_ERROR=-8;
	private static final int IO_ERROR=-7;
	private static final int SHARE_REQUEST=-6;
	/*Chat*/
	private static InetAddress group;
	private static ArrayList<String> chathistory=new ArrayList<String>();
	private static Thread tchat=null;
	
	private static Scanner input=null;
	
	public static void main(String[] args) {
		
		/* Ciclo principale che parsa gli input utente
		 * ed esegue ogni operazione richiesta (se lecita).
		 */
		System.out.println("--- Benvenuto in TURING (disTribUted collaboRative edItiNG) ---");
		System.out.println();
		while(!exit) {
			
			System.out.print("> ");
			input = new Scanner(System.in);
			String s = input.nextLine();
			System.out.println();
			
			try {
				Parser(s);
			} catch (IOException e) { //Errore, si termina...
				System.out.println("#FATAL ERROR: Il Server non risponde... Exiting.");
				try {
					disconnectFromServer();
				} catch (IOException e1) { /*Ignore */ }
				exit=true;
				e.printStackTrace();
			}
			System.out.println("-----------------------------");
		}
		input.close();
	}
	
	/**
	 * Ogni messaggio che l'utente inserisce attraverso la CLI viene parsato qui.
	 * I messaggi porteranno a uno stato di correttezza se ben formattati e in grado
	 * di poter essere eseguiti legittimamente dal Server, altrimenti viene notificato
	 * uno specifico messaggio di errore che aiuta l'utente a capire l'errore */
	private static void Parser(String tobeparsed) throws IOException {
		/**/
		int ret;
		String[] command = tobeparsed.split("\\s+");
		/**/		
		if(command.length==0) {
			System.out.println("Sintassi comando sbagliata. Consultare il manuale: ");
			plotHelp();
		}
		else if(!command[0].equals("turing") || (!command[1].equals("send") && command.length>4) || command.length<2 ) {
			if(command[0].equals("exit")) {
				disconnectFromServer();
				exit=true;
				return;
			}
			System.out.println("Sintassi comando sbagliata. Consultare il manuale: ");
			plotHelp();
		}
		/**/
		else {
			//Subscribe with RMI
			if(command[1].equals("register") && command.length==4 && !allowed) {
				registerWithRMI(command[2],command[3]);
			}
			/*Operazioni di login - logout*/
			else if(command[1].equals("login") && command.length==4 && !allowed) {
				connectToServer();
				ret = executeRequestReply(tobeparsed.substring(7));
				if(ret==0) {
					allowed=true;
					name= new String(command[2]);
					System.out.println(name+": connesso con successo.");
				}
				//Gestione errori
				else if(ret==-1) {
					System.out.println("#ERROR: Password non valida riprovare.");
					disconnectFromServer();
				}
				else if(ret==-2) {
					System.out.println("#ERROR: "+command[2]+" non registrato.");
					disconnectFromServer();
				}
				else if(ret==-3) {
					System.out.println("#ERROR: "+command[2]+" già loggato.");
					disconnectFromServer();
				}
				else if(ret==-8) {
					disconnectFromServer();
				}
			}
			else if(command[1].equals("logout") && command.length==2 && allowed && !editmode) {
				executeRequestReply(tobeparsed.substring(7));
				disconnectFromServer();
				allowed=false;
				System.out.println(name+": disconnesso con successo.");
				name=null;
			}	
			/* Operazioni a login effettuato*/
			else if(command[1].equals("create") && command.length==4 && allowed && !editmode) {
				ret=executeRequestReply(tobeparsed.substring(7));
				if(ret==0) System.out.println("[Documento: <"+command[2]+"> - #Sez: <"+command[3]+">] creato correttamente!");
				else if(ret==-1) System.out.println("#ERROR: Documento già esistente!");
			}
			else if(command[1].equals("share") && command.length==4 && allowed && !editmode) {
				ret=executeRequestReply(tobeparsed.substring(7));
				if(ret==0) System.out.println("File condiviso con successo");
				else if(ret==-1) System.out.println("#ERROR: "+command[3]+" non esistente");
				else if(ret==-2) System.out.println("#ERROR: il file è già condiviso con "+command[3]);
				else if(ret==-3) System.out.println("#ERROR: non si hanno i permessi per la condivisione o il file non esiste");
			}
			else if(command[1].equals("show") && (command.length==4 || command.length==3) && allowed && !editmode) {
				ret = executeRequestReply(tobeparsed.substring(7));
				if(ret>0) {//#file trovati
					System.out.println("Digitare il numero del file che si vuole visionare:");
					System.out.println();
					for(int i=0; i<ret; i++) {
						System.out.println("  "+i+") "+command[2]+" - Autore: ["+reader.readLine()+"]");
					}
					System.out.println();
					System.out.print("> ");
					String s = input.nextLine();
					System.out.println();
					Integer choiche;
					try{
						choiche = Integer.parseInt(s);
						if(choiche<0 || choiche>=ret) {
							choiche=-1;
						}
					}catch(NumberFormatException e) {
						choiche=-1;
					}					
					writer.println(choiche);
					if(choiche!=-1) {
						ret=Integer.parseInt(reader.readLine());
						if(ret>0) {
							int section;
							if(command.length==3)section=0;//Voglio tutto il file
							else section=Integer.parseInt(command[3]);
							try{
								downloadFile(section,ret,command[2]);
							}
							catch(IOException e) {
								e.printStackTrace();
							}
						}
						else if(ret==-2) System.out.println("#ERROR: sezione non presente");					
					}
					else System.out.println("#ERROR: Scelta non consentita");					
				}
				else if(ret==0) System.out.println("#ERROR: file non presente");
				
			}
			else if(command[1].equals("list") && command.length==2 && allowed && !editmode) {
				ret = executeRequestReply(tobeparsed.substring(7));
				for(int i=0; i<ret; i++) {
					System.out.println("-----------------------------");
					for(int j=0; j<5; j++) {
						String received=reader.readLine();
						System.out.println(received);
					}
				}
			}
			else if(command[1].equals("edit") && command.length==4 && allowed && !editmode) {
				ret=executeRequestReply(tobeparsed.substring(7));
				if(ret>0) {//#file trovati
					System.out.println("Digitare il numero del file che si vuole modificare:");
					System.out.println();
					for(int i=0; i<ret; i++) {
						System.out.println("  "+i+") "+command[2]+" - Autore: ["+reader.readLine()+"]");
					}
					System.out.println();
					System.out.print("> ");
					String s = input.nextLine();
					System.out.println();
					Integer choiche;
					try{
						choiche = Integer.parseInt(s);
						if(choiche<0 || choiche>=ret) {
							choiche=-1;
						}
					}catch(NumberFormatException e) {
						choiche=-1;
					}					
					writer.println(choiche);
					if(choiche!=-1) {
						ret=Integer.parseInt(reader.readLine());
						if(ret>=0) {
							int section=Integer.parseInt(command[3]);
							downloadFile(section,ret,command[2]);						
							//Devo ricevere i parametri per la chat
							String chat = reader.readLine();
							group=InetAddress.getByName(chat.substring(1));
							ChatThread c = new ChatThread(chat,CHAT_PORT,chathistory);
							tchat = new Thread(c);
							tchat.start();
							fileinedit=new String(command[2]);
							sectioninedit=section;
							editmode=true;
							System.out.println(">> È possibile ora modificare il file.");
							System.out.println(">> Operazioni possibili <send msg> <receive> <end-edit>");
							System.out.println(">> A termine modifica lanciare <turing end-edit> per confermare le modifiche");	
						}
						else if(ret==-2) {System.out.println("#ERROR: File correntemente in modifica da un altro utente");}//Già lockato
						else if(ret==-3) {System.out.println("#ERROR: Sezione non presente");}
					}
					else System.out.println("#ERROR: Scelta non consentita");
				}
				else if(ret==0) {System.out.println("#ERROR: File non esistente o non si hanno i dirriti per la modifica");}//Non esistente
			}
			else if(command[1].equals("end-edit") && command.length==2 && allowed && editmode) {
				ret=executeRequestReply(tobeparsed.substring(7));
				if(ret>0) {
					uploadFile(sectioninedit,ret,fileinedit);
					fileinedit=null;
					sectioninedit=0;
					editmode=false;
					tchat.interrupt();
					chathistory.clear();
					System.out.println("File correttamente aggiornato.");
				}
			}
			else if(command[1].equals("send") && command.length>=3 && allowed && editmode) {				
				DatagramSocket s = new DatagramSocket();
				byte[] buf=("["+name+"]: "+tobeparsed.substring(12)).getBytes();
				DatagramPacket packet = new DatagramPacket(buf,buf.length,group,CHAT_PORT);
				s.send(packet);
				s.close();
			}
			else if(command[1].equals("receive") && command.length==2 && allowed && editmode) {
				System.out.println("	--- Chat file: "+fileinedit+" ---	");
				System.out.println();
				for(int i=0; i<chathistory.size();i++) {
					System.out.println(chathistory.get(i));
				}
			}
			/*Gestione casi input sbagliati*/
			else if((command[1].equals("register")||command[1].equals("login"))&& allowed) {
				System.out.println("#ERROR: Operazione non permessa!");
				System.out.println("#       Attualmente si è connessi come: "+name);
				System.out.println("#       Effettuare prima il logout per poter effettuare la 'register' o un altro 'login'");
			}
			else if((command[1].equals("receive")||command[1].equals("send")||command[1].equals("end-edit")
					||command[1].equals("edit")||command[1].equals("list")||command[1].equals("show")
					||command[1].equals("share")||command[1].equals("create"))&&!allowed) {
				System.out.println("#ERROR: Operazione non permessa!");
				System.out.println("#       Effettuare prima il login!");
			}
			else plotHelp();
		}
		
	}
	
	/**
	 * Funzione che stampa un messaggio di aiuto 
	 */
	private static void plotHelp() {
		System.out.println("usage: turing COMMAND [ARGS...]");
		System.out.println();
		System.out.println("commands: ");
		System.out.println("	register <username> <password> | Registra l'user");
		System.out.println("	login <username> <password>    | Connette l'user");
		System.out.println("	logout                         | Disconnette l'user");
		System.out.println();
		System.out.println("	create <doc> <numsezioni>      | Crea un documento");
		System.out.println("	share <doc> <username>         | Condivide un documento");
		System.out.println("	show <doc> <sec>               | Mostra una sezione del documento");
		System.out.println("	show <doc>                     | Mostra l'intero documento");
		System.out.println("	list                           | Mostra la lista dei documenti");
		System.out.println();
		System.out.println("	edit <doc> <sec>               | Modifica una sezione del documento");
		System.out.println("	end-edit					   | Fine modifica della sezione del documento");
		System.out.println();
		System.out.println("	send <msg>                     | Invia un messaggio sulla chat");
		System.out.println("	receive                        | Visualizza i messaggi ricevuti sulla chat");
	}

	/**
	 * Funzione che instaura una connessione con il Server Turing
	 */
	private static void connectToServer() throws UnknownHostException, IOException {
		socket= SocketChannel.open();
		socket.connect(new InetSocketAddress(InetAddress.getLocalHost(), PORT));
		socket.socket().setSoTimeout(100);
		writer = new PrintWriter(new OutputStreamWriter(socket.socket().getOutputStream()),true);
		reader = new BufferedReader(new InputStreamReader(socket.socket().getInputStream()));
	}
	
	/**
	 * Funzione di disconnessione client
	 */
	private static void disconnectFromServer() throws IOException {
		if(socket!=null) socket.close();
		if(writer!=null) writer.close();
		if(reader!=null) reader.close();
	}
	
	/**
	 * Funzione che si interfaccia al servizio di registrazione tramite RMI
	 * 
	 * @param username Username dell'utente che si vuole registrare
	 * @param password Password in chiaro dell'utente
	 */
	private static void registerWithRMI(String username, String password) {
		SubscribeInterface serverobj;
		Remote remoteobj;
		try {
			Registry r=LocateRegistry.getRegistry(SubscribeInterface.RMIPORT);
			remoteobj = r.lookup(SubscribeInterface.SERVICE);
			serverobj = (SubscribeInterface) remoteobj;
			int ret = serverobj.subscribe(username, password);
			
			if(ret==0) System.out.println(username+": registrato con successo.");
			else {
				System.out.println("#ERROR: Registrazione fallita.");
				System.out.println("#       "+username+" già registrato.");
			}			
		}catch(Exception e) {
			System.out.println("#ERROR: Registrazione fallita.");
			System.out.println("        Servizio di registrazione Offline.");
		}
	}
	
	/**
	 * Funzione che esegue la maggior parte delle interazioni con il Server
	 * Manda un messaggio in formato testuale (in genere il comando da eseguire)
	 * e attende la risposta (in genere un codice) che può essere di successo o di errore.
	 * <p>
	 * Prima si controlla che non vi siano notifiche di condivisione file pendenti e nel caso notificate
	 * dopodichè si controlla che non vi siano messaggi di errore e nel caso così gestiti:
	 * <strong>CONCURRENCY_ERROR:</strong> in questo caso il Client senza interagire con l'utente
	 * cerca di riprovare fino ad un massimo di 5 volte, in caso di insucesso viene notificato l'utente
	 * e invitato a riprovare a lanciare il comando.
	 * <strong>SYNTAX_ERROR:</strong> in questo caso il client viene notificato del mal formattazione del suo input
	 * <strong>IO_ERROR:</strong> in questo caaso il client viene notificato che il server ha auvuto un errore IO
	 * 
	 * @param command Comando che deve essere eseguito
	 * @return ret Ritorna il codice di risposta(>=0) o di errore(<0)
	 */
	private static int executeRequestReply(String command) throws IOException {
		writer.println(command);
		//System.out.println("DEBUG: Inviato!");
		String received =reader.readLine();
		//System.out.println("DEBUG: "+received);
		Integer ret = Integer.parseInt(received);
		
		if(ret==SHARE_REQUEST) {
			System.out.println();
			System.out.println("Sei stato invitato ad editare nuovi file!");
			System.out.println("Lancia il comando: <turing list> per vedere quali sono!");
			System.out.println();
			//Si continua con l'esecuzione del comando (si aspetta la risposta)
			ret = Integer.parseInt(reader.readLine());
			retry=0;
		}
		
		if(ret==CONCURRENCY_ERROR && retry<5) {
			retry++;
			executeRequestReply(command);
		}
		if(ret==SYNTAX_ERROR) {System.out.println("#ERROR: Input mal formattato!"); retry=0;}
		else if(ret==CONCURRENCY_ERROR) {System.out.println("#ERRORE: problema di concorrenza... Riprovare."); retry=0;}
		else if(ret==IO_ERROR) {System.out.println("#ERRORE: Impossibile creare il file (IO ERROR). Contattare il supporto tecnico"); retry=0;}
		
		return ret;
	}
	
	/**
	 * Funzione che fa il download di un file "from Server to Client"
	 * 
	 * @param section Sezione che si vuole scaricare (se=0 indica tutto il file)
	 * @param numesctions Numero delle sezione di cui è composto il file
	 * @param filename Nome del file che si vuole caricare
	 */
	private static void downloadFile(int section, int numsections, String filename) throws IOException {
		
		int f=1; //Caso: 1 sola sezione
		if(section==0)f=numsections; //Caso: tutto il file (tutte le sezioni)
		for(int i=1; i<=f; i++) {
			
			String j=null;
			if(section==0) //1 Tutto il file
				j=new String(Integer.toString(i));
			else //Solo una sezione
				j=Integer.toString(section);
			

			/*pt 1/2) Si attende prima la ricezione della lunghezza in bytes del/dei file*/
			long len=Long.parseLong(reader.readLine());			
			
			/*pt 2/2) Si riceve il file in questione*/
			OpenOption[] options = new OpenOption[] { StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING };
			FileChannel fc = FileChannel.open(Paths.get(filename+"("+j+"-"+numsections+")"), options);
			long totalBytesTransferFrom = 0;
	        while (totalBytesTransferFrom < len) {
	            long transferFromByteCount = fc.transferFrom(socket, totalBytesTransferFrom, len-totalBytesTransferFrom);
	            if (transferFromByteCount <= 0){
	                break;
	            }
	            totalBytesTransferFrom += transferFromByteCount;
	        }	
			System.out.println("Ricevuto file: "+filename+"("+j+"-"+numsections+") - "+len+"byte");
			fc.close();
		}	
	}
	
	/**
	 * Funzione che fa l'upload di un file "from Client to Server"
	 * 
	 * @param section Sezione che si vuole caricare (se=0 indica tutto il file)
	 * @param numsections Numero di sezioni totali del file
	 * @param filename Nome del file che si vuole caricare
	 */
	private static void uploadFile(int section, int numsections, String filename) throws IOException {
		int f=1; //Caso: 1 sola sezione
		if(section==0)f=numsections; //Caso: tutto il file (tutte le sezioni)
		for(int i=1; i<=f; i++) {
			
			String j=null;
			if(section==0) //1 Tutto il file
				j=new String(Integer.toString(i));
			else //Tutto il file
				j=Integer.toString(section);
			
			/*Si apre il file in lettura*/
			FileChannel fc = FileChannel.open(Paths.get(filename+"("+j+"-"+numsections+")"), StandardOpenOption.READ);
			/*pt 1/2) Si invia prima la lunghezza in byte*/
			writer.println(fc.size());
			/*pt 2/2) Si invia */
			long totalBytesTransferred = 0;
			while (totalBytesTransferred < fc.size()) {
				long bytesTransferred = fc.transferTo(totalBytesTransferred, fc.size()-totalBytesTransferred, socket);
				totalBytesTransferred += bytesTransferred;
			}
			System.out.println("Invio file: "+filename+"("+j+"-"+numsections+") - "+fc.size()+"byte");
			fc.close();
		}
	}
}
