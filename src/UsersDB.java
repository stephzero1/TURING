import java.net.SocketAddress;
import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Struttura dati utilizzata per memorizzare gli utenti.
 * È composta da una <strong>ConcurrentHashMap</strong> che garantisce
 * alta scalabilità alla struttura.
 * Chiave della struttura è il <strong>nomeutente</strong> che per costruzione
 * è garantito essere univoco, mentre il campo "data 
 * è dato dalla struttura <strong>UserData</strong>.
 * La classe è costruita attraverso l'uso del pattern Signleton, in modo
 * da creare un unica istanza utilizzabile dal Server.
 * Tutte le operazioni sono fatte in modo tale da garantire un ottima 
 * concorrenza dei metodi.
 * La maggior parte delle operazioni sono fatte usando l'operatore: 
 * <strong>replace(key,oldValue,newValue)</strong> che è garantito dalla
 * dcumentazione Java essere atomico.
 * {@link java.util.concurrent.ConcurrentHashMap#replace(K,V,V) replace}.
 * <p>
 * Viene implementato inoltre il servizio RMI SubUnsuInterface, che è responsabile
 * della registrazione degli utenti al servizio.
 * 
 * @author Stefano Spadola 534919 
 */

public class UsersDB extends RemoteServer implements SubscribeInterface{
	
	private static final long serialVersionUID = 1L;
	private static UsersDB istance = null;	
	private ConcurrentMap<String, UserData> users;
	
	private UsersDB() throws RemoteException{
		 users = new ConcurrentHashMap<String, UserData>();
	}
	
	/**
	 * Costruttore Singleton
	 */
	public static UsersDB getIstance() {
		if(istance==null)
			try {
				istance=new UsersDB();
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		return istance;
	}
	
	public ArrayList<String> getList(String user){
		UserData data = users.get(user);
		if(data!=null)	return data.getList();
		else return null;
	}
	
	public UserData getData(String key) {
		return users.get(key);
	}
	
	
	/*				Operazioni gestite dal Server				*/
	
	/**
	 * Metodo RMI che esegue la sottoscrizione degli utenti al servizio
	 * 
	 * @param user Nomeutente che vuole sottoscrivere al servizio
	 * @param password la relativa password utilizzata per identificarsi
	 * @return 0 on success || -1 se un utente con lo stesso nome è già registrato
	 */
	@Override
	public int subscribe(String user, String password) throws RemoteException {
		UserData data = new UserData(password);
		if(users.putIfAbsent(user,data)!=null) {//==null (Success!)
			System.out.println("User già esistente");
			return -1;
		}
		System.out.println("Inserted");
		return 0;
	}

	/**
	 * Metodo responsabile della fase di login utente.
	 * In maniera atomica modifica lo stato dell'utente settandolo online.
	 * 
	 * @param user Nomeutente che vuole sottoscrivere al servizio
	 * @param password la relativa password utilizzata per identificarsi
	 * @param saddr Indirizzo di rete dalla quale si sta collegando
	 * @return 0 on Success || -1 Password sbagliata || -2 L'user non esiste || -3 L'utente è già loggato
	 */
	public int logUser(String user, String password, SocketAddress saddr) {
		
		int ret;
		
		UserData data = users.get(user);
		if(data!=null) {
			UserData acopy = new UserData(data);
			if(acopy.identityChecker(password)) {
				if(acopy.setOnline(saddr)==0) {
					if(users.replace(user,data,acopy)) ret = 0; //Success! (logged)
					else ret=-8; //Concorrenza
				}
				else ret=-3; //User già loggato
			}
			else ret=-1;//Wrong password
		}
		else ret=-2; //User non esiste
		
		return ret;
		
	}
	
	/**
	 * Funzione di disconessione
	 * 
	 * @param user Utente che si vuole disconnettere
	 * @return 0
	 */
	public int logOut(String user) {
		UserData data;
		UserData acopy;
		do {
			data = users.get(user);
			acopy = new UserData(data);
			acopy.setOffline();			
		}while(!users.replace(user,data,acopy));
		return 0;//Success!
	}
	
	/**
	 * Funzione che aggiunge un file nella lista dei file modificabili/visualizzabili
	 * 
	 * @param nomefile Nome del file da inserire
	 * @param user Utente che ne fa richiesta
	 * @return 0 on success || -1 se il file esiste già || -2 Se l'user non esiste || -8 Se c'è un problema di concorrenza
	 */
	public int createFile(String nomefile, String user) {
		int ret;
		
		UserData data = users.get(user);
		if(data!=null) {
			UserData acopy = new UserData(data);
			int tmp = acopy.createFile(nomefile, user);
			if(tmp==0) {
				if(users.replace(user,data,acopy)) ret=0; //Everything is ok!
				else ret=-8; //Problema di concorrenza
			}
			else {
				ret=-1; //File already exisists! 
			}
		}
		else ret=-2; //User non esiste...?
		
		return ret;
	}
	
	/**
	 * Funzione usata in casi particolari per ripristinare alcuni cambiamenti
	 * che non garantiscono la consistenza dei dati. Usa un while per garantire
	 * il successo dell'operazione.
	 * 
	 * @param nomefile Nome del file da eliminare
	 * @param user Nome utente che ne fa richiesta
	 */
	public void deleteFile(String nomefile, String user) {
		UserData data;
		UserData acopy=null;
		do {
			data=users.get(user);
			if(data!=null) {
				acopy = new UserData(data);
				acopy.getList().remove(nomefile+user);
			}
		}while(!users.replace(user,data,acopy));
	}
	
	/**
	 * Metodo che modifica una entry (già precedentemente inserita) nella struttura.
	 * 
	 * @param user è l'Username univoco
	 * @param oldValue è il vecchio valore da sostituire
	 * @param newValue è il nuovo valore da sostituire
	 * @return true se va a buon fine, false altrimenti
	 */
	public boolean modifyEntry(String user, UserData oldValue, UserData newValue) {
		return users.replace(user, oldValue, newValue);
	}

}
