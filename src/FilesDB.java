import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Struttura dati utilizzata per memorizzare i file degli utenti.
 * È composta da una <strong>ConcurrentHashMap</strong> che garantisce
 * alta scalabilità alla struttura.
 * Chiave della struttura è il fileID che è composto dalla concatenazione di
 * <strong>nomefile+nomeutente</strong> garantendo l'unicità della chiave 
 * da parte degli utilizzatori.
 * La classe è costruita attraverso l'uso del pattern Signleton, in modo
 * da creare un unica istanza utilizzabile dal Server.
 * Tutte le operazioni sono fatte in modo tale da garantire un ottima 
 * concorrenza dei metodi.
 * La maggior parte delle operazioni sono fatte usando l'operatore: 
 * <strong>replace(key,oldValue,newValue)</strong> che è garantito dalla
 * dcumentazione Java essere atomico.
 * {@link java.util.concurrent.ConcurrentHashMap#replace(K,V,V) replace}
 * 
 * @author Stefano Spadola 534919 
 */

public class FilesDB {
	
	private static FilesDB istance=null;
	private ConcurrentMap<String,FileData> files;
	
	/**
	 * Costruttore Singleton
	 */
	public static FilesDB getIstance() {
		if(istance==null)
			istance=new FilesDB();
		return istance;
	}
	
	private FilesDB() {
		files = new ConcurrentHashMap<String,FileData>();
	}
	
	/**
	 * Funzione che aggiunge un file alla tabella dei file.
	 * 
	 * @param filename Nome del file da aggiungere
	 * @param sections Numero sezioni di cui è composto il file
	 * @param authro Autore del file
	 * @return 0 on Success || -1 Se il file esiste già || -2 In caso di Errori IO
	 */
	public int createFile(String filename, Integer sections, String author) {
		
		int ret=0;
		
		//Controlla se esiste già una directory per quell'utente
		//Directory che contiene tutti i file dell'utente (Autore)
		Path path = Paths.get(author);
		if(!Files.exists(path)){
			try {
				Files.createDirectory(path);
			} catch (IOException e) {
				e.printStackTrace();
				ret=-2;
			}
		}
		
		//Si creano tutti i file della sezione
		for(int i=1; i<=sections; i++) {
			Path fileToCreate = path.resolve(filename+"("+i+"-"+sections+")");
			try {
				Files.createFile(fileToCreate);
			} catch (IOException e) {
				System.out.println("#File già esistenti... IO_ERROR");
				i=sections+1; //exit
				ret=-2;
			}
		}
		
		if(ret!=-2) {
			//Si crea un oggetto di tipo FileData e lo si inserisce nella tabella Hash
			FileData data = new FileData(filename, author,sections, path);
			
			if(files.putIfAbsent(filename+author,data)!=null) {
				System.out.println("#File già esistente!");
				ret=-1;
			}
			else{
				System.out.println("File creato correttamente con #"+sections+" sezioni");		
			}
		}
		
		return ret;
	}
	
	/**
	 * Metodo getter che restituisce tutt le info (FileData) di un file
	 * 
	 * @param fileID ID univoco del file inserito
	 * @return FileData un oggetto della classe FileData contenente tutte le info
	 */
	public FileData getFileInfo(String fileID) {
		return files.get(fileID);
	}
	
	/**
	 * Metodo che modifica una entry (già precedentemente inserita) nella struttura.
	 * 
	 * @param key È il fileID univoco (chiave)
	 * @param oldValue è il vecchio valore da sostituire
	 * @param newValue è il nuovo valore da sostituire
	 * @return true se va a buon fine, false altrimenti
	 */
	public boolean modifyEntry(String key, FileData oldValue, FileData newValue) {
		return files.replace(key,oldValue,newValue);
	}
	
	/**
	 * Metodo che aggiunge un coautore al file.
	 * Usa un ciclo per garantire il corretto inserimento del valore.
	 * [ATTENZIONE]
	 * 
	 * @param fileID ID del file univoco
	 * @param coauthro Coautore da aggiungere
	 */
	public void addCoauthor(String fileID, String coauthor) {
		FileData fd;
		FileData acopy;
		do{
			fd= FilesDB.getIstance().getFileInfo(fileID);
			acopy= new FileData(fd);
			acopy.addCoauthor(coauthor);
		}while(!files.replace(fileID,fd,acopy));
	}

}
