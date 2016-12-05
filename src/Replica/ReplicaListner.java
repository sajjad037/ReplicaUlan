package Replica;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import DFRS.BookingImp;
import Models.Enums;
import Models.UDPMessage;
import ReliableUDP.Reciever;
import ReliableUDP.Sender;
import StaticContent.*;
import Utilities.CLogger;
import Utilities.Serializer;

/*
 * Reason of this class: 
 * 1) Listen to all incoming messages from sequencer and respective RM. - DONE
 * 3) Parse the incoming UDP request and call it in the actual server object. - DONE
 * 4) Replica always reply to FrontEnd port that is in UDP Object. - DONE
 * 5) Any request that comes from Sequencer should be saved in a file as backup so that it can be restored when the replica is started again.
 */

public class ReplicaListner implements Runnable {
	private CLogger clogger;
	private DatagramSocket serverSocket;
	private Thread t = null;
	private boolean continueUDP = true;
	public static long sequencerNumber = 0;
	int port = 0;
	Enums.UDPSender machineName;
	boolean fromSequencer = false;
	boolean isWriteToTransactions = true;
	DatagramSocket recSocket;

	public ReplicaListner(CLogger clogger, int port, Enums.UDPSender machineName) {
		this.clogger = clogger;
		this.port = port;
		this.machineName = machineName;
	}

	@Override
	public void run() {
		try {
			String msg = machineName.toString() + " UDP Server Is UP!";

			System.out.println(msg);
			clogger.log(msg);
			recSocket = new DatagramSocket(this.port);
			System.out.println("Replica Listner Port: "+port);
			
			while (continueUDP) {

				// Reciever r = new Reciever(port,
				// StaticContent.SEQUENCER_ACK_PORT_FOR_REPLICA_ULAN);

				Reciever r = new Reciever(recSocket);
				UDPMessage udpMessage = r.getData();

				if(!udpMessage.getOpernation().equals(Enums.Operations.heatBeat))
				{
					long tempSeq = sequencerNumber + 1;
					if (tempSeq != udpMessage.getSequencerNumber()) {
						continue;
					}
				}
				

				// Increment the sequence number.
				sequencerNumber++;

				UDPMessage replyMessage = null;

				switch (udpMessage.getSender()) {
				case Sequencer:
					isWriteToTransactions = true;
					fromSequencer = true;
					// Perform Operations.
					msg = "Executing Opernation : " + udpMessage.getOpernation() + ", on Server :"
							+ udpMessage.getServerName();
					System.out.println(msg);
					clogger.log(msg);

					replyMessage = new UDPMessage(this.machineName, udpMessage.getSequencerNumber(),
							udpMessage.getServerName(), udpMessage.getOpernation(), Enums.UDPMessageType.Reply);

					BookingImp obj = ReplicaMain.servers.get(udpMessage.getServerName().toString());
					String res = "";
					switch (udpMessage.getOpernation()) {

					case bookFlight:

						res = obj.bookFlight(udpMessage.getParamters().get("firstName"),
								udpMessage.getParamters().get("lastName"), udpMessage.getParamters().get("address"),
								udpMessage.getParamters().get("phone"), udpMessage.getParamters().get("destination"),
								udpMessage.getParamters().get("date"), udpMessage.getParamters().get("classFlight"));

						replyMessage.setReplyMsg(res);

						break;

					case getBookedFlightCount:

						res = obj.getBookedFlightCount(udpMessage.getParamters().get("recordType"));

						replyMessage.setReplyMsg(res);

						break;

					case editFlightRecord:

						res = obj.editFlightRecord(udpMessage.getParamters().get("recordID"),
								udpMessage.getParamters().get("fieldName"), udpMessage.getParamters().get("newValue"));
						replyMessage.setReplyMsg(res);
						break;

					case transferReservation:

						res = obj.transferReservation(udpMessage.getParamters().get("passengerID"),
								udpMessage.getParamters().get("currentCity"),
								udpMessage.getParamters().get("otherCity"));
						replyMessage.setReplyMsg(res);
						break;

					}
					break;

				case ReplicaUlan:
				case ReplicaSajjad:
				case ReplicaUmer:
				case ReplicaFeras:
					msg = "Executing Opernation : " + udpMessage.getOpernation() + ", on Server :"
							+ udpMessage.getServerName();
					System.out.println(msg);
					clogger.log(msg);
					
					fromSequencer = false;
					replyMessage = new UDPMessage(this.machineName, udpMessage.getSequencerNumber(),
							udpMessage.getServerName(), udpMessage.getOpernation(), Enums.UDPMessageType.Reply);

					replyMessage.setReplyMsg("true");

					break;

				default:
					msg = "Unknow Sender : " + udpMessage.getSender();
					System.out.println(msg);
					clogger.log(msg);
					isWriteToTransactions = false;
					break;
				}

				// if it is a valid request from sequencer or RM, write its
				// transaction log for recovery.
				if (isWriteToTransactions) {
					serializeTransaction(udpMessage, String.valueOf(sequencerNumber));
				}

				if (fromSequencer) {
					DatagramSocket senderSocket = new DatagramSocket();
					Sender s = new Sender(StaticContent.FRONT_END_IP_ADDRESS, udpMessage.getFrontEndPort(), false,
							senderSocket);
					if (s.send(replyMessage)) {
						// release Port
						if (senderSocket != null && !senderSocket.isClosed())
							senderSocket.close();
					}

				} else {
					// heartbeat reply is already sent by the protocol. no need
					// to reply again.

					//
					// DatagramSocket senderSocket= new DatagramSocket();
					// Sender s = new Sender(StaticContent.RM3_IP_ADDRESS,
					// StaticContent.RM3_lISTENING_PORT, false, senderSocket);
					// if(s.send(replyMessage))
					// {
					// //release Port
					// if(senderSocket != null && !senderSocket.isClosed())
					// senderSocket.close();
					// }

				}

			}
		} catch (Exception ex) {
			clogger.logException("on starting UDP Server", ex);
			ex.printStackTrace();
			recSocket.close();
		}

	}

	/**
	 * Start the server thread
	 */
	public void start() {
		t = new Thread(this);
		t.start();
	}

	/**
	 * Execute a join on the thread
	 * 
	 * @throws InterruptedException
	 */
	public void join() throws InterruptedException {
		if (t == null)
			return;

		t.join();
	}

	/**
	 * Stop the server thread
	 */
	public void stop() {
		continueUDP = false;
	}

	public static void serializeTransaction(Object obj, String fName) throws IOException {
		String fileName = StaticContent.RM_TRANSACTION_LOGS_PATH + fName + ".obj";
		FileOutputStream fos = new FileOutputStream(fileName);
		BufferedOutputStream bos = new BufferedOutputStream(fos);
		ObjectOutputStream oos = new ObjectOutputStream(bos);
		oos.writeObject(obj);
		oos.close();
	}

}
