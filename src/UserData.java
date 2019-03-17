import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Random;

/**
 * Classe d'appoggio usata da UsersDB per
 * per registrare tutte le informazioni relative agli utenti di TURING.
 * La classe memorizza informazioni quali, la password in formato hash,
 * l'indirizzo da cui è eventualmente collegato l'user, un indicatore che controlla 
 * se ci sono nuovi inviti di condivisione file, e una lista di
 * fileID associati, ovvero i file che possono essere visualizzati/modificati
 * dall'utente.
 * 
 * @author Stefano Spadola 534919 
 */

public class UserData{

	//Variabili per la gestione della password
	/* Il "seme" è stato aggiunto in un ottica di sicurezza
	 * dove il database degli utenti
	 * doveva essere salvato/serializzato su disco
	 */
	private Integer seed;
	private Integer passwordHashed;
	//Variabile per capire se è online un user 
	private SocketAddress isOnline=null;
	//Lista file associati a lui
	private ArrayList<String> fileIDList=null;
	private boolean sharingRequest;
	
	public UserData(String password) {
		//Inizializzo password
		Random s = new Random();
		this.seed=s.nextInt(100000);
		this.passwordHashed=(new String(password+seed)).hashCode();
		this.fileIDList=new ArrayList<String>();
		sharingRequest=false;
	}
	
	/*Costruttore per la copia (deep copy)*/
	public UserData(UserData that) {
		seed=new Integer(that.seed);
		passwordHashed=new Integer(that.passwordHashed);
		isOnline=that.isOnline;
		fileIDList=new ArrayList<String>(that.fileIDList);
		sharingRequest=that.sharingRequest;
	}
	
	public void plot() {
		System.out.println(seed);
		System.out.println(passwordHashed);
		System.out.println(isOnline);
		System.out.println(fileIDList);
	}
	
	//Metodo getter
	public ArrayList<String> getList(){
		return this.fileIDList;
	}
	
	public boolean hasSharingRequest() {
		return this.sharingRequest;
	}
	
	/*			----(login phase)----			*/
	
	/**
	 * Metodo per la verifica dell'identità di un utente che sta tentando di collegarsi
	 * 
	 * @param password La password dell'utente che sta effettuando un tentativo di login
	 * @return true se la password è corretta, false altrimenti
	 */
	public boolean identityChecker(String password) {
		return((new String(password+this.seed).hashCode()==this.passwordHashed));
	}
	
	/**
	 * Metodo che setta online un utente che sta effettuando un login
	 * ed è stato precedentemente autenticato
	 * 
	 * @param address L'indirizzo da cui si sta collegando al server
	 * @return 0 on Success || -1 In caso l'utente sia già online
	 */
	public int setOnline(SocketAddress address) {
		int ret;
		if(this.isOnline==null) {
			this.isOnline=address;
			ret=0; //Everything ok! :)
		}
		else ret=-1; //User già loggato
		return ret;
	}
	
	/**
	 * Metodo che setta offline un utente che ne ha fatto richiesta
	 */
	public void setOffline() {
		this.isOnline=null;
	}
	
	public void setRequest() {
		this.sharingRequest=true;
	}
	public void unsetRequest() {
		this.sharingRequest=false;
	}
	
	/*			----(File phase)----			*/
	
	/**
	 * Metodo che aggiunge un file alla lista dei file (fileID)
	 * 
	 * @param nomefile Nome file da aggiungere
	 * @param user Nome utente che ne richiede l'aggiunta
	 * @return 0 on Success || -1 Se il file esiste già
	 */
	public int createFile(String nomefile, String user) {
		int ret;
		
		if(this.fileIDList.contains(nomefile+user)) {
			ret=-1; //File AlreadyExists
		}
		else {
			this.fileIDList.add(nomefile+user);
			ret=0;
		}
		
		return ret;
	}
}
