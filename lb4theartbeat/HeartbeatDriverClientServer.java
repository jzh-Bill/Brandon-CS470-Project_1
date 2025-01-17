package lb4theartbeat;

import java.io.File;
import java.net.DatagramSocket;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Scanner;

/**
 * Driver for a Client Server implementation of the heartbeat protocol
 * NOTE: Please start the primary server first from a cold system-wide start
 * @author Brandon Ingli
 * @version 29 Feb 2020
 */
public class HeartbeatDriverClientServer{

  private static final int PORT_NUMBER = 5555;
  private static final int TTL = 30;
  private static final int TTL_MULTIPLIER = 3;
  
  public static void main(String[] args) {

    // Print usage information if improper args provided
    if(args.length != 2){
      System.out.println("Usage: HeartbeatDriverClientServer <IP file> <IP Prefix>");
      System.out.println("<IP Prefix> is the first part of the IP address for the network interface you want to use.");
      System.exit(-1);
    }

    // Initialize the IP and Server lists
    // For Client/Server, they're the same list
    ArrayList<String> ipList = new ArrayList<String>();
    try {
      File ipFile = new File(args[0]);
      Scanner fileScanner = new Scanner(ipFile);
      while (fileScanner.hasNextLine()){
        ipList.add(fileScanner.nextLine());
      }
      fileScanner.close();
    } catch (Exception e) {
      System.err.println("[HeartbeatDriverClientServer] Oops. An exception occurred while reading the IPs.");
      e.printStackTrace();
      System.exit(-1);
    }

    // Get this machine's IP
    NetIdentity nid = new NetIdentity(args[1]);

    // Secure our socket
    DatagramSocket socket = null;
    try {
      socket = new DatagramSocket(PORT_NUMBER);
    } catch (Exception e) {
      System.err.println("[HeartbeatDriverClientServer] Oops. An exception occurred while opening the socket.");
      e.printStackTrace();
      System.exit(-1);
    }

    // Create a HashTable for the cache
    Hashtable<String, Heartbeat> localCache = new Hashtable<String, Heartbeat>();
  
    // Create the shared data
    HeartbeatSharedData data = new HeartbeatSharedData(
      socket,
      ipList, // IP List
      ipList, // Server List
      nid.getIp(),
      localCache,
      TTL,
      TTL_MULTIPLIER,
      false, // is Server
      PORT_NUMBER,
      true // is Client Server Mode
    );

    // Initialize a heartbeat for this machine
    Heartbeat thisMachine = new Heartbeat(
      nid.getIp(), // This machine's IP
      0, // Beat number
      TTL * TTL_MULTIPLIER, // as if this were the server.
      data.getMaxWait() // Time until next beat expected
    );
    localCache.put(nid.getIp(), thisMachine);

    // Start up the receive thread
    HeartbeatReceive receiveFrame = new HeartbeatReceive(data);
    Thread receiveThread = new Thread(receiveFrame);
    receiveThread.start();

    // Start up the Printer thread
    HeartbeatStatusPrinter printerFrame = new HeartbeatStatusPrinter(data);
    Thread printerThread = new Thread(printerFrame);
    printerThread.start();

    // Wait the max wait time in case we get a summary first
    try{
      Thread.sleep(data.getMaxWait() * 1000);
    } catch (InterruptedException e){
      System.err.println("[HeartbeatDriverClientServer] Oops. An exception occurred while waiting to start the HeartbeatSend.");
      receiveThread.interrupt();
      printerThread.interrupt();
      e.printStackTrace();
      System.exit(-1);
    }

    // Start the Heartbeat send thread
    HeartbeatSend sendFrame = new HeartbeatSend(data);
    Thread sendThread = new Thread(sendFrame);
    sendThread.start();

    // This thread is done. We'll let it gracefully exit.

  }

}