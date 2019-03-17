import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Interfaccia di registrazione con RMI
 * 
 * @author Stefano Spadola 534919 
 */

public interface SubscribeInterface extends Remote{
	
	public final int RMIPORT = 8556;
	public final String SERVICE = "REGISTER";
	
	public int subscribe(String user, String password) throws RemoteException;	
}
