import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.Scanner;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramSocket;
import java.io.FileInputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.BufferedOutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.io.Serializable;

public class Client {
	DatagramSocket socket;
	static final int RETRY_LIMIT = 4;    /*
	 * UTILITY METHODS PROVIDED FOR YOU
	 * Do NOT edit the following functions:
	 *      exitErr
	 *      checksum
	 *      checkFile
	 *      isCorrupted
	 *
	 */

	/* exit unimplemented method */
	public void exitErr(String msg) {
		System.out.println("Error: " + msg);
		System.exit(0);
	}

	/* calculate the segment checksum by adding the payload */
	public int checksum(String content, Boolean corrupted) {
		if (!corrupted) {
			int i;
			int sum = 0;
			for (i = 0; i < content.length(); i++)
				sum += (int) content.charAt(i);
			return sum;
		}
		return 0;
	}


	/* check if the input file does exist */
	File checkFile(String fileName) {
		File file = new File(fileName);
		if (!file.exists()) {
			System.out.println("SENDER: File does not exists");
			System.out.println("SENDER: Exit ..");
			System.exit(0);
		}
		return file;
	}


	/*
	 * returns true with the given probability
	 *
	 * The result can be passed to the checksum function to "corrupt" a
	 * checksum with the given probability to simulate network errors in
	 * file transfer
	 */
	public boolean isCorrupted(float prob) {
		double randomValue = Math.random();
		return randomValue <= prob;
	}



	/*
	 * The main method for the client.
	 * Do NOT change anything in this method.
	 *
	 * Only specify one transfer mode. That is, either nm or wt
	 */

	public static void main(String[] args) throws IOException, InterruptedException {
		if (args.length < 5) {
			System.err.println("Usage: java Client <host name> <port number> <input file name> <output file name> <nm|wt>");
			System.err.println("host name: is server IP address (e.g. 127.0.0.1) ");
			System.err.println("port number: is a positive number in the range 1025 to 65535");
			System.err.println("input file name: is the file to send");
			System.err.println("output file name: is the name of the output file");
			System.err.println("nm selects normal transfer|wt selects transfer with time out");
			System.exit(1);
		}

		Client client = new Client();
		String hostName = args[0];
		int portNumber = Integer.parseInt(args[1]);
		InetAddress ip = InetAddress.getByName(hostName);
		File file = client.checkFile(args[2]);
		String outputFile = args[3];
		System.out.println("----------------------------------------------------");
		System.out.println("SENDER: File " + args[2] + " exists  ");
		System.out.println("----------------------------------------------------");
		System.out.println("----------------------------------------------------");
		String choice = args[4];
		float loss = 0;
		Scanner sc = new Scanner(System.in);


		System.out.println("SENDER: Sending meta data");
		client.sendMetaData(portNumber, ip, file, outputFile);

		if (choice.equalsIgnoreCase("wt")) {
			System.out.println("Enter the probability of a corrupted checksum (between 0 and 1): ");
			loss = sc.nextFloat();
		}

		System.out.println("------------------------------------------------------------------");
		System.out.println("------------------------------------------------------------------");
		switch (choice) {
			case "nm":
				client.sendFileNormal(portNumber, ip, file);
				break;

			case "wt":
				client.sendFileWithTimeOut(portNumber, ip, file, loss);
				break;
			default:
				System.out.println("Error! mode is not recognised");
		}


		System.out.println("SENDER: File is sent\n");
		sc.close();
	}


	/*
	 * THE THREE METHODS THAT YOU HAVE TO IMPLEMENT FOR PART 1 and PART 2
	 *
	 * Do not change any method signatures
	 */

	/* TODO: send metadata (file size and file name to create) to the server
	 * outputFile: is the name of the file that the server will create
	 */
	public void sendMetaData(int portNumber, InetAddress IPAddress, File file, String outputFile) {
		try {
			//first, create socket for client with no specific port
			socket = new DatagramSocket();
			//create and send metadata
			MetaData metaData = new MetaData();
			metaData.setName(outputFile); //set name
			metaData.setSize((int) file.length()); //set size

			//transmission
			//to construct packet, object must be converted into a byte array.
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			ObjectOutputStream objectStream = new ObjectOutputStream(outputStream);
			objectStream.writeObject(metaData);
			byte[] data = outputStream.toByteArray();

			//create packet next and assign to server's ip and port number
			DatagramPacket sendPacket = new DatagramPacket(data, data.length, IPAddress, portNumber);
			//send to server
			socket.send(sendPacket);

			System.out.println("CLIENT: Metadata sent successfully: (file name, size): (" + metaData.getName() + ", " + metaData.getSize() + ")");
			//close socket
			socket.close();

			//catch exception
		} catch (IOException e) {
			e.printStackTrace();
			exitErr("Failed to send");
		}
	}


	/* TODO: Send the file to the server without corruption*/
	public void sendFileNormal(int portNumber, InetAddress IPAddress, File file) {
		try {
			socket = new DatagramSocket();
			int sequenceNumber = 0; // Initialize sequence number

			FileInputStream fileInputStream = new FileInputStream(file);
			byte[] chunkData = new byte[4]; // Chunk size is 4 bytes
			int bytesRead;
			int totalSegmentsSent = 0;

			System.out.println("SENDER: Start Sending File");
			System.out.println("----------------------------------------");

			while ((bytesRead = fileInputStream.read(chunkData)) != -1) {
				// Create a new data segment
				Segment dataSegment = new Segment();
				dataSegment.setSq(sequenceNumber);
				dataSegment.setSize(bytesRead);
				dataSegment.setType(SegmentType.Data);
				dataSegment.setPayLoad(new String(chunkData, 0, bytesRead));
				dataSegment.setChecksum(checksum(dataSegment.getPayLoad(), false));

				System.out.println("SENDER: Sending segment: sq:" + dataSegment.getSq() +
						", size:" + dataSegment.getSize() +
						", checksum: " + dataSegment.getChecksum() +
						", content: (" + dataSegment.getPayLoad() + ")");


				// Convert the data segment to a byte array
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				ObjectOutputStream objectStream = new ObjectOutputStream(outputStream);
				objectStream.writeObject(dataSegment);
				byte[] data = outputStream.toByteArray();

				DatagramPacket sendPacket = new DatagramPacket(data, data.length, IPAddress, portNumber);
				socket.send(sendPacket);

				System.out.println("SENDER: Waiting for an ack");

				// receives a datagram packet from a UDP socket, segment
				//segment contain ACK
				DatagramPacket receivePacket = new DatagramPacket(new byte[1024], 1024);
				socket.receive(receivePacket);
				byte[] ackData = receivePacket.getData();
				ByteArrayInputStream ackInputStream = new ByteArrayInputStream(ackData);
				ObjectInputStream ackObjectStream = new ObjectInputStream(ackInputStream);
				Segment ackSegment = (Segment) ackObjectStream.readObject();

				//check if type or sequence number match with server, then proceed if matched
				if (ackSegment.getType() == SegmentType.Ack && ackSegment.getSq() == sequenceNumber) {
					System.out.println("SENDER: ACK sq=" + ackSegment.getSq() + " RECEIVED.");
					totalSegmentsSent++;
				}

				System.out.println("----------------------------------------");

				sequenceNumber = 1 - sequenceNumber; // Alternate sequence number
			}

			// Close the file input stream and socket
			fileInputStream.close();
			socket.close();

			System.out.println("SENDER: File is sent");
			System.out.println("total segments " + totalSegmentsSent);

		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
			exitErr("Failed to send the file.");
		}
	}


	/* TODO: This function is essentially the same as the sendFileNormal function
	 *      except that it resends data segments if no ACK for a segment is
	 *      received from the server.*/
	public void sendFileWithTimeOut(int portNumber, InetAddress IPAddress, File file, float loss) {
		try {
			socket = new DatagramSocket();
			int sequenceNumber = 0; // keep track of the sequence number of the data segments being sent.

			//These variables are used in the context of reliable data transmission, data segments may re-transmit or corrupted.
			FileInputStream fileInputStream = new FileInputStream(file);
			byte[] chunkData = new byte[4]; //store a chunk of data from file before sending as data segment. Read 4 bytes of data from file at a time.
			int bytesRead;
			int totalSegmentsSent = 0;
			int consecutiveRetries = 0;
			int RETRY_LIMIT = 4; //Constant for max number of consecutive retries allowed. If consecutiveRetries variable exceeds this limit, client will terminate transmission to avoid being stuck in a loop of retries.

			System.out.println("SENDER: Start Sending File");
			System.out.println("----------------------------------------");

			while ((bytesRead = fileInputStream.read(chunkData)) != -1) {
				// Create a new data segment
				Segment dataSegment = new Segment();
				dataSegment.setSq(sequenceNumber);
				dataSegment.setSize(bytesRead);
				dataSegment.setType(SegmentType.Data);
				dataSegment.setPayLoad(new String(chunkData, 0, bytesRead)); // converts portion of chunkData byte array (from index 0 to bytesRead) into a String and sets it as the payload of the dataSegment.

				// Simulate network corruption based on the loss probability
				if (isCorrupted(loss)) {
					dataSegment.setChecksum(checksum(dataSegment.getPayLoad(), true));
					System.out.println("SENDER: >>>>>>Network ERROR: segment checksum is corrupted<<<<<<");
				} else {
					dataSegment.setChecksum(checksum(dataSegment.getPayLoad(), false));
				}

				System.out.println("SENDER: Sending segment: sq:" + dataSegment.getSq() +
						", size:" + dataSegment.getSize() +
						", checksum: " + dataSegment.getChecksum() +
						", content: (" + dataSegment.getPayLoad() + ")");

				// Convert the data segment to a byte array
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				ObjectOutputStream objectStream = new ObjectOutputStream(outputStream);
				objectStream.writeObject(dataSegment);
				byte[] data = outputStream.toByteArray();

				DatagramPacket sendPacket = new DatagramPacket(data, data.length, IPAddress, portNumber);
				socket.send(sendPacket);

				System.out.println("SENDER: Waiting for an ack");

				// receiving ACK timeout
				socket.setSoTimeout(4000); // in milisecond

				try {
					// Receive an ACK
					DatagramPacket receivePacket = new DatagramPacket(new byte[1024], 1024);
					socket.receive(receivePacket);
					byte[] ackData = receivePacket.getData();
					ByteArrayInputStream ackInputStream = new ByteArrayInputStream(ackData);
					ObjectInputStream ackObjectStream = new ObjectInputStream(ackInputStream);
					Segment ackSegment = (Segment) ackObjectStream.readObject();

					// Check if the received ACK matches the expected sequence number
					if (ackSegment.getType() == SegmentType.Ack && ackSegment.getSq() == sequenceNumber) {
						System.out.println("SENDER: ACK sq=" + ackSegment.getSq() + " RECEIVED.");
						totalSegmentsSent++;
						consecutiveRetries = 0; // Reset consecutive retries
					}
				} catch (SocketTimeoutException e) {
					// Handle timeout (no ACK received)
					System.out.println("SENDER: >>>>>>TIMEOUT ALERT: Re-sending the same segment again, current retry: " + (consecutiveRetries + 1));
					consecutiveRetries++;
					if (consecutiveRetries > RETRY_LIMIT) {
						System.out.println("SENDER: Exceeded retry limit. Terminating.");
						break;
					}
				}

				System.out.println("----------------------------------------");

				sequenceNumber = 1 - sequenceNumber; // Alternate sequence number
			}

			// Close the file input stream and socket
			fileInputStream.close();
			socket.close();

			System.out.println("Total segments sent (including retransmissions): " + totalSegmentsSent);
			System.out.println("SENDER: File is sent");

		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
			exitErr("Failed to send the file.");
		}
	}
}
