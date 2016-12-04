package DFRS;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.omg.CORBA.ORB;
import org.omg.CORBA.ORBPackage.InvalidName;
import org.omg.CosNaming.NameComponent;
import org.omg.CosNaming.NamingContextExt;
import org.omg.CosNaming.NamingContextExtHelper;
import org.omg.CosNaming.NamingContextPackage.CannotProceed;
import org.omg.CosNaming.NamingContextPackage.NotFound;
import org.omg.PortableServer.POA;
import org.omg.PortableServer.POAHelper;
import org.omg.PortableServer.POAManagerPackage.AdapterInactive;
import org.omg.PortableServer.POAPackage.ObjectNotActive;
import org.omg.PortableServer.POAPackage.ServantAlreadyActive;
import org.omg.PortableServer.POAPackage.ServantNotActive;
import org.omg.PortableServer.POAPackage.WrongPolicy;

/**
 * @author ulanbaitassov
 * The Remote Server application which responds to Client-Manager request remotely 
 */
public class Server{
	private String nameServer = "";
	private String nameServerABR = ""; //server name in abbreviation
	private int portUDP = 0; //port number for UDP connection
	private static boolean serverON = false;
	private static Logger logger;
	private static BookingImpService bService;
	private static BookingImp bookingObject;
	
	/**
	 * Default Constructor
	 * @throws RemoteException occurs remote object can not be created
	 */
	public Server(){}
	
	/**
	 * Get method which returns server name
	 * @return server name
	 */
	public String getNameServer(){
		return nameServer;
	}
	
	/**
	 * Get method which returns server abbreviate name
	 * @return server abbreviate name
	 */
	public String getNameServerABR(){
		return nameServerABR;
	}
	
	/**
	 * Get method which returns server UPD port number
	 * @return UDP port number
	 */
	public int getPortUDP(){
		return portUDP;
	}
	
	/**
	 * Sets UDP port number with new value 
	 * @param new_port new UDP port number
	 */
	public void setPortUDP(int new_port){
		portUDP = new_port;
	}
		
	/**
	 * Sets server name, assigns abbreviation and creates log file for given server
	 * @param new_sname server name
	 * @param new_abrname server abbreviation name
	 */
	public void setServerName(String new_sname, String new_abrname){
		try {
			nameServer = new_sname;
			nameServerABR = new_abrname;
			//open server logger
			logger =  Logger.getLogger("serverLog");
			logger.setLevel(Level.FINE);
			logger.setUseParentHandlers(false);
			FileHandler fileHandler;
			fileHandler = new FileHandler("log"+nameServer+".txt",true);
			fileHandler.setFormatter(new LogFormatter());
			logger.addHandler(fileHandler);
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * Main method
	 * @param args console arguments
	 * @throws AdapterInactive 
	 * @throws InvalidName 
	 * @throws WrongPolicy 
	 * @throws ObjectNotActive 
	 * @throws ServantAlreadyActive 
	 * @throws ClassNotFoundException 
	 * @throws ServantNotActive 
	 * @throws org.omg.CosNaming.NamingContextPackage.InvalidName 
	 * @throws CannotProceed 
	 * @throws NotFound 
	 */
	@SuppressWarnings("unchecked")
	public static void main(String[] args) throws AdapterInactive, InvalidName, ObjectNotActive, WrongPolicy, ServantAlreadyActive, ClassNotFoundException, ServantNotActive, org.omg.CosNaming.NamingContextPackage.InvalidName, NotFound, CannotProceed{				
		
		try {
			final Server server = new Server();
			serverON = true;
			//write server server name, RMI port and UDP port in common repository 
				File file3=new File("common.repository");
				if(!file3.exists()){
					System.out.println("Server experienced problem in configuring and terminated.");
					System.exit(0);
				}
			//input server information	
			Scanner scanner = new Scanner(System.in);
			System.out.println("Enter server name");
			String serverN = scanner.nextLine();
			Scanner sc = new Scanner(new File("common.repository"));
			String[] arr = null;
			while(sc.hasNext()){
				String str = sc.nextLine();
				arr = str.split(";");
				if(arr[0].equalsIgnoreCase(serverN)){
					break;
				}
			}
			sc.close();
			String serverNABR = arr[2];
			server.setServerName(serverN, serverNABR);
			server.setPortUDP(Integer.parseInt(arr[1]));
			final DatagramSocket socketUDP = new DatagramSocket(server.getPortUDP());
			scanner.close();
			//BOOKING IMPLEMENTATION SERVICE
			bService = new BookingImpService(server.getNameServer(), server.getNameServerABR());
			//CORBA code
			ORB orb = ORB.init(args, null);
			POA rootPOA = POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
			rootPOA.the_POAManager().activate();
			bookingObject = new BookingImp(bService);
			server.bService.setLogger(logger);
			bookingObject.setORB(orb);
			org.omg.CORBA.Object ref = rootPOA.servant_to_reference(bookingObject);
			Booking href = BookingHelper.narrow(ref);
			org.omg.CORBA.Object objRef = orb.resolve_initial_references("NameService");
			NamingContextExt ncRef = NamingContextExtHelper.narrow(objRef);
			NameComponent path[] = ncRef.to_name(server.getNameServer());
			ncRef.rebind(path, href);
			//UDP thread, make server listen to UDP datagrams by running it forever
			Thread t2 = new Thread(new Runnable(){
				@Override
				public void run(){
					while(serverON){
						try {
							byte[] buffer = new byte[1000];
							//UDP server side connection
							DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length);
							socketUDP.receive(requestPacket); 
							String message = new String(requestPacket.getData());				
							//System.out.println(requestPacket.getData());
							String[] arr = message.split(";");
							if(arr[0].equalsIgnoreCase("getcount")){
								logger.info("UDP REQUEST is received from server: "+arr[1]+" message: "+message);
								//System.out.println("UDP REQUEST is received from server: "+arr[0]+" message: "+message);
								//System.out.println(message);
								//receive UDP request message and get number of passengers who booked flight on this server
								message = server.bService.getBookedCountFlight2(arr[2]);
								DatagramPacket replyPacket = new DatagramPacket(message.getBytes(), message.length(), requestPacket.getAddress(), requestPacket.getPort());
								//send number of passengers to requesting server
								socketUDP.send(replyPacket);
								logger.info("UDP REPLY sent back to server: "+arr[1]+" message: "+message);
								//System.out.println("UDP REPLY sent back to server: "+arr[0]+" message: "+message);
							}else if(arr[0].equalsIgnoreCase("book")){
								//System.out.println("in udp booking = "+message);
								message = server.bookingObject.bookFlight(arr[3], arr[4], arr[6], arr[7], arr[1], arr[2], arr[5]);
								//System.out.println("xxx = "+message);
								DatagramPacket replyPacket = new DatagramPacket(message.getBytes(), message.length(), requestPacket.getAddress(), requestPacket.getPort());
								//send number of passengers to requesting server
								socketUDP.send(replyPacket);
							}
						} catch (IOException e) {
							//TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			});
			t2.start();
			//start parallel thread which saves the passenger and flight hash maps every some time
			Thread t1 = new Thread(new Runnable(){
				@Override
				public void run(){
					while(serverON){
					//save passenger hash map every 9 seconds on hard disk
						if(System.currentTimeMillis()%9000==0){
							try{
								File fileOne=new File(server.getNameServer()+"Pass.ser");
						    	FileOutputStream fos=new FileOutputStream(fileOne);
						    	ObjectOutputStream oos=new ObjectOutputStream(fos);
						    	oos.writeObject(server.bService.getHashMap());
						    	oos.flush();
						    	oos.close();
						    	fos.close();
						    	//System.out.println("Passenger record hash map was successfully saved in file.");
						 	}catch(IOException e){
								e.printStackTrace();
						    	System.err.println("EXCEPTION OCCURED WRITING FILE "+e.getMessage());
							}
						}
						//save flight hash map every 10 seconds on hard disk
						if(System.currentTimeMillis()%10000==0){
							try{
								File fileOne=new File(server.getNameServer()+"Flight.ser");
						    	FileOutputStream fos=new FileOutputStream(fileOne);
						    	ObjectOutputStream oos=new ObjectOutputStream(fos);
						    	oos.writeObject(server.bService.getHashMapFlight());
						    	oos.flush();
						    	oos.close();
						    	fos.close();
						    	//System.out.println("Flight hash map was successfully saved in file.");
						 	}catch(IOException e){
								e.printStackTrace();
						    	System.err.println("EXCEPTION OCCURED WRITING FILE "+e.getMessage());
							}
						}
					}
				}
			});
			t1.start();
			//SAVE or CREATE HASHMAPS flight AND passenger
			File file=new File(server.getNameServer()+"Pass.ser");
			if(file.exists()){
				FileInputStream fis=new FileInputStream(file);
			    ObjectInputStream ois=new ObjectInputStream(fis);
			    server.bService.setHashMap((HashMap<Character,List<Passenger>>)ois.readObject());
			    ois.close();
			    fis.close();
			    System.out.println("Passenger record hash map was successfully loaded from file.");
			}else{
				server.bService.setHashMap(new HashMap<Character,List<Passenger>>());
				System.out.println("Passenger record hash map was successfully created.");
			}
			//load flight hash map from file if exists or create new
			File file2=new File(server.getNameServer()+"Flight.ser");
			if(file2.exists()){
				FileInputStream fis=new FileInputStream(file2);
			    ObjectInputStream ois=new ObjectInputStream(fis);
			    server.bService.setHashMapFlight((HashMap<String, List<Flight>>)ois.readObject());
			    ois.close();
			    fis.close();
			    System.out.println("Flight hash map was successfully loaded from file.");
			}else{
				server.bService.setHashMapFlight(new HashMap<String, List<Flight>>());
				System.out.println("Flight hash map was successfully created.");
			}
			//INFORM that SERVER has started
			System.out.println("UDP Port number: "+server.getPortUDP());
			System.out.println("Server "+server.getNameServer()+" is up and running...");
			logger.info("SERVER IS UP ... server name: "+server.getNameServer()+" server abr name: "+server.getNameServerABR()+" UDP Port number: "+server.getPortUDP());
			//CORBA run
			orb.run();			
		} catch(IOException e){
			e.printStackTrace();
		}finally{
			//logger.info("SERVER SHUT DOWN...");
		}	
	}
}

