import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

/**
 * Thread che gestisce le operazioni inviategli da un Client.
 * Si occupa della comunicazione con esso e della manipolazione
 * delle <strong>strutture dati utente</strong> e <strong>strutture dati file</strong>
 * 
 * @author Stefano Spadola 534919 
 */

public class ClientHandler implements Runnable{

	//Varibili per una sessione con un utente
	private SocketChannel clientsocket=null;
	private String username=null;
	
	//Reader e Writer utilizzati per lo scambio messaggi
	private BufferedReader reader;
	private PrintWriter writer;
	
	//Varibile di terminazione
	private boolean exit=false;
	
	//Varibili per l'editmode
	private boolean editmode=false;	
	private String fileinedit=null;
	private int sectioninedit=0;
	
	//Messaggi di ritorno di ERRORE
	private static final int SYNTAX_ERROR=-9;
	private static final int CONCURRENCY_ERROR=-8;
	private static final int IO_ERROR=-7;
	private static final int SHARE_REQUEST=-6;
	
	/**
	 * Costruttore per il Thred gestore utente
	 * 
	 * @param client è il SocketChannel relativo al client
	 */
	public ClientHandler(SocketChannel client) {
		this.clientsocket=client;
		try {
			reader= new BufferedReader(new InputStreamReader(this.clientsocket.socket().getInputStream()));
			writer= new PrintWriter(new OutputStreamWriter(this.clientsocket.socket().getOutputStream()),true);
		} catch (IOException e) {
			System.out.println("### FATAL ERROR");
			System.out.println("### "+Thread.currentThread().getName()+": Shutdown...");
			recoverAndTerminate();
			e.printStackTrace();
		}		
	}
	
	@Override
	/**
	 * Main del Thread gestore utente.
	 * Processa i messaggi ricevuti dell'utente
	 * e risponde secondo le varie casistiche.
	 */
	public void run() {
		while(!exit) {
			try {
				
				//Si riceve il messaggio e si parsa
				String message=reader.readLine();
				if(message==null) {
					System.out.println("#Il client si è disconnesso in maniera anomala...");
					System.out.println("#Ripristino delle strutture dati...");
					recoverAndTerminate();
					return;
				}
				
				/* Si controlla prima, se siano pervenute delle richieste di condivisione */
				if(username!=null) {
					checkPreviousShare();
				}
				
				String[] command = message.split("\\s+");
				System.out.println("-----------------------------");
				System.out.println("|Client: ["+username+"] - "+clientsocket.socket().getRemoteSocketAddress());
				System.out.println("|Sent: "+message);
				
				/*Casistiche del messaggio*/				
				
				/**
				 * login
				 * 
				 * Esegue il login dell'utente.
				 * 
				 * Risponde al client:
				 *  0  in caso di successo
				 * -1 in caso di password sbagliata
				 * -2 in caso di user non esistente
				 * -3 in caso di user giò loggato
				 * -8 in caso di errore di concorrenza
				 */
				if(command[0].equals("login")) {
					int ret = UsersDB.getIstance().logUser(command[1], command[2], clientsocket.socket().getRemoteSocketAddress());
					
					if(ret==0) {//Success
						this.username=new String(command[1]);
						//Prima di rispondere con successo si controlla se sono pervenute nuove richieste
						checkPreviousShare();
						
						writer.println(ret);
					}
					else if (ret==-1 || ret==-2 || ret==-3) {//Wrong Password || User doesn't exists || User already logged
						writer.println(ret);
						recoverAndTerminate();
					}
					else {//Concurrency error
						writer.println(ret);
					}
				}
				
				/**
				 * create
				 * 
				 * Esegue la creazione di un documento richiesto dall'utente.
				 * 
				 * Risponde al client:
				 *  0 file inserito con successo
				 * -1 in caso di file già esistente
				 * -7 IO_ERROR in caso di errori I/O all'interno del server
				 * -9 SYNTAX_ERROR in caso di errori di sintassi del comando				 * 
				 */
				else if(command[0].equals("create")) {
					int ret=-1;//File già esistente
					
					try {
						int numsec=Integer.parseInt(command[2]);
						if(numsec>0) {
							if((ret=UsersDB.getIstance().createFile(command[1], username))==0) {
								if((ret=FilesDB.getIstance().createFile(command[1], numsec, username))!=0){//Se == 0 (successo)!
									System.out.println("#ERROR: Eccezione I/O");
									UsersDB.getIstance().deleteFile(command[1], username); //Reverting change...
									ret=IO_ERROR; //Errore I/O nessuna modifica è stata apportata
								}
							}
						}
						else ret=SYNTAX_ERROR;
					}
					catch(NumberFormatException e) {
						ret=SYNTAX_ERROR;
						System.out.println("#ERROR: input formattato male");
					}		
					
					writer.println(ret);					
				}
				
				/**
				 * share
				 *
				 * Condivide il file con un utente (lo aggiunge come coautore).
				 * 
				 * Risponde al client:
				 *  0 in caso di successo
				 * -1 se l'utente con cui si vuole condividere il file non esiste
				 * -2 se il file è già condiviso con l'utente
				 * -3 il file non esiste o non si hanno i permessi necessari
				 * -8 CONCURRENCY_ERROR in caso l'operazione venga fatta in maniera concorrente ad un altra
				 */
				else if(command[0].equals("share")) {
					int ret;
					
					ArrayList<String> listID = UsersDB.getIstance().getList(command[2]);
					if(listID==null)ret=-1;//User non esistente
					else {
						int i=0; int id=0;
						while(i<listID.size()){
							if(listID.get(i).equals(command[1]+username)){id=-1;i=listID.size();}
							else i++;
						}	
						if(id==-1) ret=-2; //File già condiviso
						else {
							UserData data = UsersDB.getIstance().getData(command[2]);
							UserData acopy = new UserData(data);
							FileData fd= FilesDB.getIstance().getFileInfo(command[1]+username);
							if(fd!=null) {
								acopy.getList().add(command[1]+username);
								acopy.setRequest();
								if(UsersDB.getIstance().modifyEntry(command[2], data, acopy)) {
									FilesDB.getIstance().addCoauthor(command[1]+username, command[2]);
									ret=0;
								}										
								else ret=CONCURRENCY_ERROR; //Non è stato possibile farlo... CONCORRENZA
							}
							else {
								ret=-3; //Non si hanno permessi necessari o non vi è alcun file
							}							
						}	
					}					
					writer.println(ret);				
				}
				
				/**
				 * show
				 * 
				 * Si scarica un intero documento o una sezione per la visione.
				 * 
				 * Risponde al client:
				 * pt1)
				 * 		>0 #file con quel nome + lista file
				 *  	 0 Il file non esiste
				 * pt2) Dopo aver chiesto quale file visionare
				 * 		>0 #sezioni + file
				 * 		-1 La scelta non è consentita
				 * 		-2 La sezione richiesta non esiste
				 */
				else if(command[0].equals("show")) {
					
					int ret=0; int section=0;
					FileData fd=null;					
					String fileID=filePicker(username,command[1]);
					if(fileID!=null) {
						fd=FilesDB.getIstance().getFileInfo(fileID);
						if(command.length==3) { //Caso una sola sezione
							try {
								section=Integer.parseInt(command[2]);
								if(section>fd.getNumberOfSections() || section<1) ret=-2; //Sezione non presente
							}
							catch(NumberFormatException e) {
								ret=SYNTAX_ERROR;
							}							
						}
						else section=0; //Caso tutto le sezioni
						
						//Invio al client le risposte
						if(ret==0) {
							int numsections=fd.getNumberOfSections();
							writer.println(numsections);
							uploadFile(section, numsections, command[1], fd.getPath());
						}
						else {
							writer.println(ret);
						}		
					}
					else {System.out.println("#Scelta non consentita");}//File non esistente
				}
				
				/**
				 * list
				 * 
				 * Si invia la lista dei file che è possibile editare.
				 * 
				 * Risponde al client:
				 * >0 #file + info per ogni file
				 */
				else if(command[0].equals("list")) {
					ArrayList<String> listID = UsersDB.getIstance().getList(username);
					
					//Si invia prima il numero di file
					writer.println(listID.size());
					
					//Successivamente si inviano le informazioni contenute in 4 campi
					for(int i=0; i<listID.size(); i++) {
						
						FileData fd = FilesDB.getIstance().getFileInfo(listID.get(i));						
						boolean[] sections=fd.getSections();
						String modified = new String("Attualmente sotto modifica le sezioni: {");
						for(int j=1; j<=sections.length; j++) {
							if(sections[j-1]==true) modified=modified+" "+j;
						}
						modified=modified+" }";
						
						writer.println("Documento: "+fd.getFileName());
						writer.println("Autore:    "+fd.getAuthor());
						writer.println("Coautori:  "+fd.getCoauthors());
						writer.println("#Sezioni:  "+fd.getNumberOfSections());
						writer.println(modified);						
					}
				}
				
				/**
				 * edit
				 * 
				 * Si blocca un sezione per essere editata dall'user che ne fa richiesta.
				 * 
				 * pt1)
				 * 		>0 #file con quel nome + lista file
				 *  	 0 Il file non esiste
				 * pt2) Dopo aver chiesto quale file editare
				 * 		>0 #sezioni file + file da editare
				 * 		-1 La scelta non è consentita
				 * 		-2 Il file è già bloccato in modifca da un altro utente
				 * 		-3 La sezione richiesta non esiste
				 */
				else if(command[0].equals("edit")) {
					int ret=0; int section=0;
					FileData fd=null; InetAddress chat=null;
					//Si selezione il file prima
					String fileID=filePicker(username,command[1]);
					if(fileID!=null) {
						try{								
							section=Integer.parseInt(command[2]);
							//Si locka immediatamente la modifica
							fd = FilesDB.getIstance().getFileInfo(fileID);
							if(section<=fd.getNumberOfSections() && section>=1) {
								FileData acopy = new FileData(fd);
								if(acopy.getSections()[section-1]==false) {
									acopy.lockSection(section);
									if(acopy.getChat()==null) {
										chat=Server.getFreeInetAddress();
										acopy.setChat(chat);
									}
									else {
										chat=acopy.getChat();
									}
									if(FilesDB.getIstance().modifyEntry(fileID,fd,acopy)) {
										ret=fd.getNumberOfSections();
										fileinedit=new String(fileID);
										sectioninedit=section;
										editmode=true;										
									}
									else {ret=CONCURRENCY_ERROR;}//Errore di concorrenza => Si richiede al client di riprovare
								}
								else {ret=-2;}//File già lockato
							}
							else {ret=-3;} //Sezione non presente
						}catch(NumberFormatException e) {
							ret=SYNTAX_ERROR;//Errore nel messaggio
						}
						writer.println(ret);
						if(ret>0) {
							uploadFile(section, ret, command[1], fd.getPath());
							writer.println(chat.toString());
						}
					}
					else System.out.println("#Scelta non consentita.");
				}
				/**
				 * end-edit
				 * 
				 * Si sblocca la sezione precedentemente sotto modifica
				 * e si aggiornano le modifiche sul server.
				 * 
				 * Risponde al client
				 * >0 Sezione aggiornata e sbloccata*/
				else if(command[0].equals("end-edit") && editmode) {
					FileData fd;
					FileData acopy;
					do{
						fd = FilesDB.getIstance().getFileInfo(fileinedit);
						acopy = new FileData(fd);
						if(acopy.getSections()[sectioninedit-1]==true) {
							int ret=fd.getNumberOfSections();
							writer.println(ret);
							//Ricevo il file
							downloadFile(sectioninedit,ret,fd.getFileName(),fd.getPath());
							//Si unlocka dopo la reicezione del file
							acopy.unlockSection(sectioninedit);
							
						}
						else {writer.println(-2); break;}//Some error occurred...//Impossibile
					}while(!FilesDB.getIstance().modifyEntry(fileinedit,fd,acopy));
					sectioninedit=0;
					fileinedit=null;
					editmode=false;
				}
				
				/**
				 * logout
				 * 
				 * Esegue il logout dell'utente precedentemente collegato
				 * 
				 * Risponde al client:
				 *  0 utente correttamente scollegato
				 */
				else if(command[0].equals("logout")) {
					int ret = UsersDB.getIstance().logOut(username);
					writer.println(ret);
					recoverAndTerminate();
				}
			}catch (IOException e){
				System.out.println("###FATAL ERROR: Client closed connection or an errorappeared.");
				recoverAndTerminate();
			}
		}
		System.out.println("# Chiusura Client Handler #");
		System.out.println("### "+Thread.currentThread().getName()+": Shutdown...");
		return;
	}
	
	/**
	 * Funzione di terminazione Thread.
	 * Si occupa di gestire anche i casi di terminazione improvvisa da parte del client
	 * chiudendo e portando a termine tutte le operazioni che potrebbero lasciare
	 * inconsisteti le strutture dati.
	 * Chiude reader, writer e socket.
	 */
	private void recoverAndTerminate() {
		if(editmode) {//Se ci sono sezioni in modifica si sbloccano
			FileData fd;FileData acopy;
			do{
				fd = FilesDB.getIstance().getFileInfo(fileinedit);
				acopy = new FileData(fd);
				if(acopy.getSections()[sectioninedit-1]==true)
					acopy.unlockSection(sectioninedit);
			}while(!FilesDB.getIstance().modifyEntry(fileinedit,fd,acopy));
			editmode=false;
			fileinedit=null;
			sectioninedit=0;
		}
		
		if(username!=null) {//Si slogga l'utente
			UsersDB.getIstance().logOut(username);
			username=null;
		}
		
		try {
			clientsocket.close();
			reader.close();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		clientsocket=null;
		exit=true;
	}
	
	/**
	 * Funzione che in caso di richieste di condivisione file pendenti
	 * notifica l'utente.
	 */
	public void checkPreviousShare() {
		UserData data = UsersDB.getIstance().getData(username);
		if(data.hasSharingRequest()) {
			UserData acopy = new UserData(data);
			acopy.unsetRequest();
			UsersDB.getIstance().modifyEntry(username, data, acopy);
			writer.println(SHARE_REQUEST);
		}	
	}
	
	/**
	 * Funzione che consente di far scegliere ad un utente un file
	 * tra quelli visualizzabili/editabili.
	 * Utilizza un semplice meccanismo di scambio messaggi su socket.
	 * 
	 * @param username Username dell'utente
	 * @param filename Nome del file che si vuole visualizzare/editare
	 * @return fileID ID univoco del file che si vuole visualizzare/editare
	 */
	public String filePicker(String username, String filename) {
		String ret=null;
		FileData fd=null;
		ArrayList<String> howmany = new ArrayList<String>();
		
		//Si cerca il documento
		ArrayList<String> listID = UsersDB.getIstance().getList(username);
		for(int i=0; i<listID.size(); i++) {
			fd = FilesDB.getIstance().getFileInfo(listID.get(i));
			if(fd.getFileName().equals(filename)) {
				howmany.add(listID.get(i));
			}
		}
		//Si inviano quanti file ho trovato (0 nessun file || n numero file)
		writer.println(howmany.size());
		if(howmany.size()>0) {
			for(int j=0; j<howmany.size(); j++) {
				writer.println(FilesDB.getIstance().getFileInfo(howmany.get(j)).getAuthor());
			}
			int choiche;
			try {
				choiche = Integer.parseInt(reader.readLine());
				if(choiche<0 || choiche>=howmany.size()) {
					choiche=-1;
				}
			} catch (NumberFormatException | IOException e) {
				choiche=-1;
				e.printStackTrace();
			}

			if(choiche!=-1) {
				ret=howmany.get(choiche);
			}
		}
		return ret;
	}

	/**
	 * Funzione che fa l'upload di un file "from Server to Client"
	 * 
	 * @param section Sezione che si vuole caricare (se=0 indica tutto il file)
	 * @param numsections Numero di sezioni totali del file
	 * @param filename Nome del file che si vuole caricare
	 * @param path Percorso dove è salvato il file sul server
	 */
	public void uploadFile(int section, int numsections, String filename, Path path) throws IOException {
		int f=1; //Caso: 1 sola sezione
		if(section==0)f=numsections; //Caso: tutto il file (tutte le sezioni)
		for(int i=1; i<=f; i++) {
			
			String j=null;
			if(section==0) //1 Tutto il file
				j=new String(Integer.toString(i));
			else //Tutto il file
				j=Integer.toString(section);
			
			/*Si apre il file in lettura*/
			FileChannel fc = FileChannel.open(path.resolve(filename+"("+j+"-"+numsections+")"), StandardOpenOption.READ);

			/*pt 1/2) Si invia prima la lunghezza in byte*/
			writer.println(fc.size());
			
			try {
				Thread.sleep(70);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
			/*pt 2/2) Si invia il file*/			
			long totalBytesTransferred = 0;
			while (totalBytesTransferred < fc.size()) {
				long bytesTransferred = fc.transferTo(totalBytesTransferred, fc.size()-totalBytesTransferred, clientsocket);
				totalBytesTransferred += bytesTransferred;
			}
			System.out.println("|Inviato file: "+path.resolve(filename+"("+j+"-"+numsections+")")+" - "+fc.size()+"byte");
			fc.close();
		}
	}
	
	/**
	 * Funzione che fa il download di un file "from Client to Server"
	 * 
	 * @param section Sezione che si vuole scaricare
	 * @param numsections Numero di sezioni totali del file
	 * @param filename Nome del file che si vuole scaricare
	 * @param path Percorso dove è salvato il file sul server
	 */
	private void downloadFile(int section, int numsections, String filename, Path path) throws IOException {
		
		int f=1; //Caso: 1 sola sezione
		if(section==0)f=numsections; //Caso: tutto il file (tutte le sezioni)
		for(int i=1; i<=f; i++) {
			
			String j=null;
			if(section==0) //1 Tutto il file
				j=new String(Integer.toString(i));
			else //Tutto il file
				j=Integer.toString(section);
			
			/*pt 1/2) Si attende prima la ricezione della lunghezza in bytes del/dei file*/
			long len=Long.parseLong(reader.readLine());
			
			/*pt 2/2) Si riceve il file in questione*/
			OpenOption[] options = new OpenOption[] { StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING };
			FileChannel fc = FileChannel.open(path.resolve(filename+"("+j+"-"+numsections+")"), options);
			long totalBytesTransferFrom = 0;
	        while (totalBytesTransferFrom < len) {
	            long transferFromByteCount = fc.transferFrom(clientsocket, totalBytesTransferFrom, len-totalBytesTransferFrom);
	            if (transferFromByteCount <= 0){
	                break;
	            }
	            totalBytesTransferFrom += transferFromByteCount;
	        }			
			System.out.println("|Ricevuto file: "+filename+"("+j+"-"+numsections+")"+" - "+len+"byte");
			fc.close();
		}
	}
}
