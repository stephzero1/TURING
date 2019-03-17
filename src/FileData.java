import java.io.Serializable;
import java.net.InetAddress;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * Classe d'appoggio usata da FilesDB per
 * registrare tutte le informazioni relativi ai file, quindi
 * i suoi autori, i suoi coautori, il numero delle sezioni
 * di cui sono composti, il percorso dove sono salvati e strutture
 * per capire quali sezioni sono correntemente in modifica.
 * 
 * @author Stefano Spadola 534919 
 */

public class FileData implements Serializable {

	private static final long serialVersionUID = 1L;

	private String filename;
	private String author;
	private ArrayList<String> coauthors;
	private boolean[] sections;
	private int numsections;
	private Path path;
	private InetAddress chat;
	
	public FileData(String filename, String author, Integer numberofsections, Path path) {
		this.filename=new String(filename);
		this.author=new String(author);
		this.coauthors=new ArrayList<String>(0);
		this.sections=new boolean[numberofsections];
		this.numsections=numberofsections;
		for(int i=0; i<numberofsections; i++)
			sections[i]=false;
		this.path=path;
		this.chat=null;
	}
	
	/*Costruttore per la copia (deep copy)*/
	public FileData(FileData that) {
		this.filename=new String(that.filename);
		this.author=new String(that.author);
		this.coauthors=new ArrayList<String>(that.coauthors);
		this.sections=that.sections;
		this.numsections=that.numsections;
		this.path=that.path;
		this.chat=that.chat;
	}
	
	/*Metododi getters*/
	public String getFileName() {return this.filename;}
	public String getAuthor() {return this.author;}
	public ArrayList<String> getCoauthors(){return this.coauthors;}
	public boolean[] getSections() {return this.sections;}
	public Path getPath() {return this.path;}
	public int getNumberOfSections() {return this.numsections;}
	public InetAddress getChat() {return this.chat;}
	
	/**
	 * Esegue il lock di una sezione.
	 * 
	 * @param section Sezione da lockare
	 */
	public void lockSection(int section) {
		sections[section-1]=true;
	}
	
	/**
	 * Esegue la unlock di una sezione.
	 * 
	 * @param section Sezione da unlockare
	 */
	public void unlockSection(int section) {
		sections[section-1]=false;
	}
	
	/**
	 * Aggiunge un coautore al file.
	 * 
	 * @param coauthro Coautore da aggiungere al file
	 */
	public void addCoauthor(String coauthor) {
		this.coauthors.add(coauthor);
	}
	
	/**
	 * Assegna un indirizzo multicast ad un file
	 * 
	 * @param address Indirizzo multicast per la chat relativa al file
	 */
	public void setChat(InetAddress address) {
		this.chat=address;
	}
	
	/**
	 * Revoca l'indirizzo multicast usato per la chat
	 */
	public void unsetChat() {
		this.chat=null;
	}

}
