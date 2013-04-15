import java.net.*;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.PatternSyntaxException;
import java.io.*;

import javax.swing.text.AbstractDocument.BranchElement;

import com.sun.org.apache.bcel.internal.generic.InstructionConstants.Clinit;
import com.sun.tools.javac.util.Name;

/**
 * A class that represents the main server instance for MazeWar
 * It keeps on listening on the port for new connections
 * Creates other threads for processing and sending-receiving messages as required
 * @author Hasan Imam
 * 
 */
public class MazeServer {
	// public final static Map<String, Integer> table = Collections.synchronizedMap(new HashMap<String, Integer>());
	//	private static String exchange = "nasdaq";

	public static final List <Packet> incoming = Collections.synchronizedList(new LinkedList<Packet>());
	public static final List <Packet> outgoing = Collections.synchronizedList(new LinkedList<Packet>());
	public static final Map <Integer, Client> client_map = Collections.synchronizedMap(new HashMap<Integer, Client>());

	public static final int my_id = 0;

	private static int client_count = 0; // Used to assign client ID. Keeps incrementing as new clients are added

	/**
	 * The default width of the {@link Maze}.
	 */
	private static final int mazeWidth = 20;

	/**
	 * The default height of the {@link Maze}.
	 */
	private static final int mazeHeight = 10;

	/**
	 * The default random seed for the {@link Maze}.
	 * All implementations of the same protocol must use 
	 * the same seed value, or your mazes will be different.
	 */
	private static final int mazeSeed = 42;

	/**
	 * The {@link Maze} that the game uses.
	 */
	private static Maze maze = null;
	
	private static SenderThread broadcaster = null;
	private static ProcessingThread processor = null;

	/**
	 * @param args First argument should be port number to listen to
	 */
	public static void main(String[] args) throws IOException {
		// Create the maze
		maze = new MazeImpl(new Point(mazeWidth, mazeHeight), mazeSeed);
		assert(maze != null);

		// Have the ScoreTableModel listen to the maze to find
		// out how to adjust scores.
		ScoreTableModel scoreModel = new ScoreTableModel();
		assert(scoreModel != null);
		maze.addMazeListener(scoreModel);
		((MazeImpl)maze).scoreModel = scoreModel;

		// Starting the in-game request processing thread
		assert(client_map instanceof HashMap);
		processor = new ProcessingThread(incoming, outgoing, client_map, maze);
		processor.start();
		
		// Starting the broadcaster thread
		broadcaster = new SenderThread("Broadcaster", outgoing);
		broadcaster.start();

		// Now the serving part
		ServerSocket serverSocket = null;

		try {
			if(args.length == 1) { // Single argument, the port number
				serverSocket = new ServerSocket(Integer.parseInt(args[0]));
			} else {
				System.err.println("ERROR: Invalid arguments!");
				System.exit(-1);
			}
		} catch (NumberFormatException e) {
			System.err.println("Listening port argument must be an integer");
			System.exit(-1);
		} catch (IOException e) {
			System.err.println("ERROR: Could not listen on port!");
			System.exit(-1);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
		assert(serverSocket != null);
		
		System.out.println("Listening...");
		boolean listening = true;
		Socket new_client = null;
		while (listening) {
			try {
				// Get new client connection
				new_client = serverSocket.accept();

				ObjectOutputStream out = new ObjectOutputStream(new_client.getOutputStream());
				ObjectInputStream in = new ObjectInputStream(new_client.getInputStream());
				RemoteClient client = null;
				String name = null;

				// Assign unique client ID
				Packet req1 = (Packet) in.readObject();
				if (req1.type == Packet.Type.JOIN && req1.cid == -1 && req1.pid == 0 && req1.message != null) {
					// Create new RemoteClient, but dont add to maze
					client = new RemoteClient(req1.message);
					name = req1.message;
					// Send reply
					Packet rep1 = new Packet(Packet.Type.JOIN, req1.pid, my_id);
					rep1.value = ++client_count;
					int stat = client.setID(client_count);
					assert(stat == 0); // Updating the client's id on this side
					out.writeObject(rep1);
					System.out.println("\tNew client " + name + " joining.");
				} else {
					System.err.println("Faulty packet from new client. Not assigned ID. Ignored.");
					new_client.close();
					continue;
				}

				// Handle request for maze update
				Packet req2 = (Packet) in.readObject();
				if (req2.cid == client.getID() && req2.type == Packet.Type.MAZE_REQ) {
					System.out.print("\tSending maze to " + name + " ... ");
					req2.cid = my_id;
					req2.maze = maze;
					//out.writeObject(req2);
					((MazeImpl)maze).writeMazeStream(out);
					System.out.println("sent");
					//((MazeImpl)maze).print(); //XXX remove later
				} else {
					System.err.println("Faulty packet from new client. Not updated maze. Ignored.");
					new_client.close();
					continue;
				}

				// Handle request for placement in maze
				boolean placed = false;
				Packet req3 = (Packet) in.readObject();
				if (req3.cid == client.getID() && req3.type == Packet.Type.INIT_POS && req3.point != null) {
					while (!placed && req3 != null && req3.type == Packet.Type.INIT_POS) {
						System.out.println("\tSetting " + name + " to an initial point");
						Packet rep3 = new Packet(Packet.Type.NULL, req3.pid, my_id);
						if (maze.addClient(client, req3.point, null)) { // Finally adding the main client to the maze
							// Adding to the client map 
							synchronized (client_map) {
								assert(client_map.containsKey(client.getID()) == false);
								client_map.put(Integer.valueOf(client.getID()), client);
								System.out.println("\t" + client.getName() + " (ID: " + client.getID()+ ") added to client map.");
							}

							placed = true;
							rep3.type = Packet.Type.INIT_POS;
							rep3.point = req3.point;
							out.writeObject(rep3);
						} else {
							req3 = null; System.gc(); // Force garbage collection to clear the reused packet
							rep3.type = Packet.Type.POS_ERROR;
							out.writeObject(rep3);
							req3 = (Packet) in.readObject(); // Reading data for the next attempt
						}
					}
				} else {
					System.err.println("Faulty packet from new client. Not placed in maze. Ignored.");
					new_client.close();
					continue;
				}
				if (placed == false) {
					System.err.println("Faulty packet from new client. Client not attempting until placement. Ignored.");
					new_client.close();
					continue;
				}
				// After this point the new client should be all set to be added to the big picture

				// Broadcast the addition of the new client
				// After receiving this packet all other clients should add this client to their local mazes
				Packet genesis = new Packet(Packet.Type.JOIN, -1, 0); // Note: pid is set to -1, since its being sent from the listening thread
				genesis.message = name; // Name of the new client
				genesis.value = req3.cid; // ID of the new client
				genesis.point = req3.point; // Point where to put the new client
				synchronized (outgoing) { // Put this broadcast at the beginning of the queue. Prioritized
					outgoing.add(0, genesis); // Index 0 means to be added to the front
				}
				
				// Create the receiver thread
				new RecvrThread (client.getID(), client.getName() + ":recvr", new_client, in, incoming).start();
				
				// Add this client to serverListener list
				broadcaster.addListener(new_client, out);
			} catch (EOFException e) {
				listening = false;
				if (!serverSocket.isBound()) System.err.println("MazeServer: Server socket not bound!");
				else if (serverSocket.isClosed()) System.err.println("MazeServer: Unexpected closure of serverSocket.");
				else if (!new_client.isConnected() || new_client.isClosed() || new_client.isInputShutdown() || new_client.isOutputShutdown() || new_client.isBound()) {
					System.err.println("MazeServer: Client disconnected.");
					listening = true; // Do not shut down if client error
				}
				else System.err.println("MazeServer: Unknown error!");
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				System.err.println("MazeServer: Reading garbage object from client.");
				e.printStackTrace();
			} catch (Exception e) {
				
				e.printStackTrace();
			} 
		}
		
		System.out.println("MazeServer: Shutting down.");
		
		if (!serverSocket.isClosed()) 
			serverSocket.close();
	}
}

// TODO threads need to quit along with client. both thread needs to die together
// TODO check if main needs to keep track of these service threads, probably not
// TODO for outgoing there has to be a mechanism to delete the packet after everyone has sent it

class SenderThread extends Thread {
	//private Socket socket = null;
	//private ObjectOutputStream out = null;
	private boolean active = false;
	private List <Packet> outgoing;
	private static final Map<Socket,ObjectOutputStream> serverListeners = Collections.synchronizedMap(new HashMap<Socket, ObjectOutputStream>());
	
	public SenderThread(String name, List <Packet> outgoing) {
		super(name);
		assert(name != null && outgoing != null);
		this.outgoing = outgoing;
		active = true;
	}
	
	/**
	 * Add new socket and stream to the listener queue
	 * Packets starts getting broadcasted to the given listener after this
	 * @param socket Socket where the listener is conencted
	 * @param out Stream where the sender is supposed to write. This stream must have been derived from the given socket
	 * @return Returns operation status.
	 */
	public boolean addListener(Socket socket, ObjectOutputStream out) {
		assert(socket != null && out != null);
		try {
			synchronized (serverListeners) {
				serverListeners.put(socket, out);
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		
		System.out.println(this.getName() + ": added new listener");
		return true;
	}
	
	public void run() {
		System.out.println(this.getName() + ": running");
		assert(outgoing != null);
		while (active) {
			Packet current = null;
			if (outgoing.isEmpty() == false && serverListeners.isEmpty() == false) { // These checkers are not thread safe, but the following modifiers are
				boolean pop = false;
				// Get the packet at the front of queue
				synchronized (outgoing) {
					current = outgoing.get(0); // Get the first element
				}
				if (current == null) { // Didn't get the packet. Should not occur
					System.err.println(this.getName() + ": Unexpected behaviour in dequeing packets.");
					continue; 
				}
				
				int count = 0;
				// Broadcast
				synchronized (serverListeners) {
					if (!serverListeners.isEmpty()) {
						// Iterate over all the listeners delivering this message
						Iterator<Map.Entry<Socket,ObjectOutputStream>> iter = serverListeners.entrySet().iterator();
						while (iter.hasNext()) {
						    Map.Entry<Socket,ObjectOutputStream> entry = iter.next();
						    try {
						    	// Write object. If failed with exception check for possible socket closure
								entry.getValue().writeObject(current);
								count++;		
							} catch (SocketException e) {
								if (entry.getKey().isClosed()) {
									// This socket has been closed. Remove it from serverListeners
									System.out.println(this.getName() + ": removing serverListener < " + entry.getKey().toString() + "," + entry.getValue().toString() + " > from listener list.");
									iter.remove();
								} else if (!entry.getKey().isConnected() || !entry.getKey().isBound()) {
									// This socket has been closed. Remove it from serverListeners
									System.err.println(this.getName() + ": removing serverListener < " + entry.getKey().toString() + "," + entry.getValue().toString() + " > from listener list.");
									iter.remove();
								} else {
									System.err.println(this.getName() + ": Unhandeled socket exception in broadcasting loop.");
									e.printStackTrace();
								}
							} catch (IOException e) {
								if (entry.getKey().isClosed()) {
									// This socket has been closed. Remove it from serverListeners
									System.out.println(this.getName() + ": removing serverListener < " + entry.getKey().toString() + "," + entry.getValue().toString() + " > from listener list.");
									iter.remove();
								} else if (!entry.getKey().isConnected() || !entry.getKey().isBound()) {
									// This socket has been closed. Remove it from serverListeners
									System.err.println(this.getName() + ": removing serverListener < " + entry.getKey().toString() + "," + entry.getValue().toString() + " > from listener list.");
									iter.remove();
								} else {
									System.err.println(this.getName() + ": Unhandeled IO exception in broadcasting loop.");
									e.printStackTrace();
								}
							} catch (Exception e) {
								System.err.println(this.getName() + ": Unknown exception in broadcasting loop.");
								e.printStackTrace();
							}
						} // Done broadcasting this packet
						
						System.out.println(this.getName() + ": dispatched packet to " + count + " clients");
						
						// Remove packet from queue
						pop = true;
					} // else, means there is no one to 
				}
				
				// Dequeue
				if (pop) {
					// Remove the broadcasted packet from queue
					synchronized (outgoing) {
						outgoing.remove(current);
					}
				}
			}
		}
	}
}

class RecvrThread extends Thread {
	private Socket socket = null; // Only to be used for closing
	private ObjectInputStream in = null;
	private List <Packet> incoming;
	private boolean active = false;
	private int client_id = -1;

	public RecvrThread(int cid, String name, Socket socket, ObjectInputStream in, List <Packet> incoming) {//TODO queue to be passed in
		super(name);
		this.socket = socket;
		this.in = in;
		this.incoming = incoming;
		this.client_id = cid;
	}

	public void run() {
		active = true;
		System.out.println(this.getName() + ": running");
		while (active) {
			try {
				// Receive a packet
				Packet req = (Packet) in.readObject();
				
				// Enqueue packet
				synchronized (incoming) {
					// Prioritize quit packets
					if (req.type == Packet.Type.QUIT) incoming.add(0, req); // Add to the front
					else incoming.add(req);
				}
				System.out.println(this.getName() + ": enqueued " + req.cid + "'s packet # " + req.pid + " " + req.type);//RM
			} catch (EOFException e) {
				if (socket.isInputShutdown() || !socket.isConnected() || !socket.isBound() || socket.isClosed() || socket.isOutputShutdown()) {
					System.err.println(this.getName() + ": Client disconnected");
				} else e.printStackTrace();
				active = false;
				
			} catch (IOException e) {
				if (socket.isInputShutdown() || !socket.isConnected() || !socket.isBound() || socket.isClosed() || socket.isOutputShutdown()) {
					System.err.println(this.getName() + ": Socket error.");
				} else e.printStackTrace(); 
				active = false;
				
			} catch (ClassNotFoundException e) {
				active = false;
				e.printStackTrace();
			} catch (Exception e) {
				active = false;
				System.err.println(this.getName() + ": Unhandeled exception.");
				e.printStackTrace();
			}
			
		}
		
		System.out.println(this.getName() + ": exiting");
		
		// Close the socket to force the sender thread to clean up respective buffers and sockets from it's internal lists
		// Note: The sender thread might get SocketException after this.
		try {
			if (!socket.isClosed()) socket.close();
		} catch (IOException e) {
			System.err.println(this.getName() + ": Failed to close socket: " + socket.toString());
			e.printStackTrace();
		}
	}
}

/**
 * A thread for processing the server request queue
 *
 */
class ProcessingThread extends Thread {
	private List <Packet> incoming;
	private List <Packet> outgoing;
	private Map <Integer, Client> client_map;
	private boolean running = true;
	private Maze maze;

	// Broadcast packet number
	private static int broadcast_pid = 1;

	private static int pid = 1;

	public ProcessingThread(List<Packet> incoming, List<Packet> outgoing, Map<Integer, Client> client_map, Maze maze) {
		super("Processor");
		assert(incoming instanceof LinkedList);
		assert(outgoing instanceof LinkedList);
		assert(client_map instanceof HashMap);
		this.incoming = incoming;
		this.outgoing = outgoing;
		this.client_map = client_map;
		this.maze = maze;
	}

	private Packet update_action(Client client, Packet req) {
		Packet broadcast = new Packet(Packet.Type.UPDATE_ACTION, broadcast_pid++, 0); // The cid fields holds the ID of the packet sender. For server it's always 0
		broadcast.message = client.getName(); // Message field contains the name, so that others can identify it in their respective maps
		broadcast.update_type = req.type;
		broadcast.value = req.cid; // For checking on the receiver side, contains the ID of the requesting client
		return broadcast;
	}

	public void run () {
		System.out.println(this.getName() + ": running");
		while (running) {
			try {
				Packet current = null;
				// Get the first element, if any
				synchronized (incoming) {
					if (incoming.size() > 0) {
						current = incoming.remove(0);
					}
				}
				// Process the packet
				Client client = null;
				if (current != null) {

					// Get sender of this packet
					synchronized (client_map) {
						client = client_map.get(Integer.valueOf(current.cid));
					}
					assert(client != null); // Means we are missing a client in our map

					//if (current.type == Packet.Type.FWD)
					Packet update = null;
					switch (current.type) {
					case FWD: case BACK: case FIRE:
						// Action
						boolean valid = false;
						if (current.type == Packet.Type.FIRE) valid = client.fire();
						else if (current.type == Packet.Type.BACK) valid = client.backup();
						else valid = client.forward();
						// Packet generation
						if (valid) { // Action validated
							update = update_action(client, current);
						} else { // Action rejected
							// Instead of broadcasting, simply ignore request
							// TODO: optionally, can choose to let the client know that the request was rejected
							update = null;
							System.out.println(this.getName() + ": action ignored");//RM
						}
						// Enqueing
						if (update != null) {
							synchronized (outgoing) {
								outgoing.add(outgoing.size(), update); // Adding to the end
								System.out.println(this.getName() + ": action approved");//RM
							}
						}
						break;

					case RIGHT: case LEFT:
						// Action
						if (current.type == Packet.Type.RIGHT) client.turnRight();
						else client.turnLeft();
						// Packet generation
						update = update_action(client, current);
						// Enqueing
						synchronized (outgoing) {
							outgoing.add(outgoing.size(), update); // Adding to the end
							System.out.println(this.getName() + ": action approved");//RM
						}
						break;

					case JOIN: case REQ: case REPLY: case NULL: case POS_ERROR: case INIT_POS: case MAZE_REQ: case UPDATE_ACTION: case MOVE_REQ: 
						// All the cases that are unexpected
						System.err.println("Client " + client.getName() + " talking jibberish.");
						break;
						// Note: JOINs should not go through processor. They are put in the output queue by the server itself
						
					//case JOIN: // New client added. Neeed to tell others
						//update = new Packet(Packet.Type.JOIN, broadcast_pid++, 0);
						

					case QUIT:
						// Remove client from maze
						maze.removeClient(client);
						// Remove client form client_map
						client_map.remove(current.cid);
						// Broadcast packet generation
						update = new Packet(Packet.Type.UPDATE_ACTION, broadcast_pid++, 0);
						update.message = client.getName(); // Message field contains the name, so that others can identify it in their respective maps
						update.update_type = Packet.Type.QUIT;
						update.value = current.cid; // For checking on the receiver side, contains the ID of the requesting client
						// Enqueing
						synchronized (outgoing) {
							outgoing.add(0, update); // QUIT broadcast prioritized and put in the front of the list
							System.out.println(this.getName() + ": action approved");//RM
						}
						break;

					default:
						System.err.println("Unhandeled packet passing by...");
						break;
					}

				}

			} catch (ConcurrentModificationException e) {
				System.err.println("Concurrency violation occured.");
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}

