package ReplicaManager;

import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.TimerTask;

import Models.Enums;
import Models.UDPMessage;
import ReliableUDP.Sender;
import StaticContent.StaticContent;

public class RMHeartBeat extends TimerTask {

	public void run() {

		DatagramSocket socket;
		try {
			socket = new DatagramSocket();

			UDPMessage hearBeatmsg = new UDPMessage(Enums.UDPSender.ReplicaUmer, -1, Enums.FlightCities.Montreal,
					Enums.Operations.heatBeat, Enums.UDPMessageType.Request);

			Sender s = new Sender(StaticContent.REPLICA_UMER_IP_ADDRESS, StaticContent.REPLICA_UMER_lISTENING_PORT,
					false, socket);
			System.out.println("Sending heartbeat on port: "+StaticContent.REPLICA_UMER_lISTENING_PORT);
			if (s.send(hearBeatmsg)) {
				// release Port
				
				ReplicaManagerMain.isReplicaAlive = true;
				System.out.println("HeartBeat successfully sent to Replica.");
				if (socket != null && !socket.isClosed())
					socket.close();
			} else {
				//Replica is dead, restart it through that thread.
				ReplicaManagerMain.isReplicaAlive = false;
				ReplicaManagerMain.restartReplica();
				
			}
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
