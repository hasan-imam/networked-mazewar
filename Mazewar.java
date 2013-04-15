/*
Copyright (C) 2004 Geoffrey Alan Washburn

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
USA.
 */

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JOptionPane;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.event.KeyListener;

import javax.swing.BorderFactory;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

/**
 * The entry point and glue code for the game.  It also contains some helpful
 * global utility methods.
 * @author Geoffrey Washburn &lt;<a href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: Mazewar.java 371 2004-02-10 21:55:32Z geoffw $
 */

public class Mazewar extends JFrame {

	/**
	 * The socket for communicating with the server.
	 */
	private static Socket clientSocket = null;
	
	/**
	 * 
	 */
	private ObjectOutputStream out;
	private ObjectInputStream in;
	
	private static Integer packet_id = -1;
	//private static Integer client_id = -1;
	
	// dimension and seed values SHOULD NOT BE CHANGED
	
	/**
	 * The default width of the {@link Maze}.
	 */
	private final int mazeWidth = 20;//

	/**
	 * The default height of the {@link Maze}.
	 */
	private final int mazeHeight = 10;//

	/**
	 * The default random seed for the {@link Maze}.
	 * All implementations of the same protocol must use 
	 * the same seed value, or your mazes will be different.
	 */
	private static final int mazeSeed = 42;//

	/**
	 * The {@link Maze} that the game uses.
	 */
	private static Maze maze = null;

	/*
	 * The {@link GUIClient} for the game.
	 * Will be null if the main player is not a {@link GUIClient}
	 */
	//private GUIClient guiClient = null;//
	
	/**
	 * The primary client for the game.
	 * Can be a {@link GUIClient} or {@link RobotClient}, running as the primary
	 */
	private static Client main_client = null;

	/**
	 * The panel that displays the {@link Maze}.
	 */
	private OverheadMazePanel overheadPanel = null;

	/**
	 * The table the displays the scores.
	 */
	private JTable scoreTable = null;

	/** 
	 * Create the textpane statically so that we can 
	 * write to it globally using
	 * the static consolePrint methods  
	 */
	private static final JTextPane console = new JTextPane();
	
	private static LocalRecvrThread recvr = null;

	/** 
	 * Write a message to the console followed by a newline.
	 * @param msg The {@link String} to print.
	 */ 
	public static synchronized void consolePrintLn(String msg) {
		console.setText(console.getText()+msg+"\n");
	}

	/** 
	 * Write a message to the console.
	 * @param msg The {@link String} to print.
	 */ 
	public static synchronized void consolePrint(String msg) {
		console.setText(console.getText()+msg);
	}

	/** 
	 * Clear the console. 
	 */
	public static synchronized void clearConsole() {
		console.setText("");
	}

	/**
	 * Static method for performing cleanup before exiting the game.
	 * Before calling this, other clients on the network should already have been notified about the quitting
	 * @param x Exit value. 0 for normal exit.
	 */
	public static void quit(int x) {
		
		try {
			// Closing connection
			if (clientSocket != null && clientSocket.isConnected() == true) {
				clientSocket.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		System.exit(x);
	}
	
	/**
	 * Method for requesting the server to join a game.
	 * Upon return this client should have assigned client_id and updated maze
	 * @param sock Socket to be used to communicate with server. Should be already initialized
	 * @param name Name of this player/client
	 */
	private static boolean req_join(ObjectInputStream in, ObjectOutputStream out, String name, ScoreTableModel scoretable ) throws Exception {
		if (in == null || out == null) {
			throw new Exception("Given stream is null.");
		} else if (name == null) {
			throw new Exception("Invalid name.");
		}
		
		// Requesting to join
		Packet req1 = new Packet(Packet.Type.JOIN, 0, -1); // Client ID not yet received, so set to -1
		req1.message = name;
		out.writeObject(req1); // Send the join request
		Packet rep1 = (Packet) in.readObject(); // Read the reply. Should contain client_id
		
		if (rep1.cid == 0 && rep1.pid == req1.pid && rep1.type == Packet.Type.JOIN && rep1.value > 0) { // Case: request granted
			main_client.setID(rep1.value);
			
			// Request for a maze update. 
			Packet req2 = new Packet(Packet.Type.MAZE_REQ, 1, main_client.getID());
			out.writeObject(req2); // Send the maze request
			assert(maze == null);
			maze = new MazeImpl(in, mazeSeed, scoretable);
			if (maze != null) {
				return true; //TODO final check
			} else {
				System.err.println("Failed to get the maze update.");
				return false;
			}
			
		} else {
			System.err.println("Failed to join the game.");
			return false;
		}
	}
	
	/** 
	 * Client side: The place where all the pieces are put together. 
	 */
	public Mazewar(String server_host, int server_port) {
		super("ECE419 Mazewar");
		consolePrintLn("ECE419 Mazewar started!");
		
		// Create the ScoreTableModel
		// Add it to maze's listener list once the maze has been created, but before any client is added
		ScoreTableModel scoreModel = new ScoreTableModel(); //TODO XXX what to do with score table???
		assert(scoreModel != null);
		
		// Connect to server
		try {
			clientSocket = new Socket(server_host, server_port);
			out = new ObjectOutputStream(clientSocket.getOutputStream());
			in = new ObjectInputStream(clientSocket.getInputStream());
		} catch (UnknownHostException e1) {
			// TODO Auto-generated catch block
			System.err.println("ERROR: Don't know where to connect.");
			e1.printStackTrace();
			quit(1);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			System.err.println("ERROR: Couldn't get I/O for the connection.");
			e1.printStackTrace();
			quit(1);
		}
		assert(clientSocket != null && in != null && out != null);

		// Get the Client name. Create the client. Add to maze now only if it's a RobotClient
		String name = JOptionPane.showInputDialog("Enter your name or enter AI to create a robot player");
		//String name = "Hasan";
		if ((name == null) || (name.length() == 0)) {
			quit(1);
		} else if (name.equals("AI")) { // If the name is AI, then it means user want a robot client
			// TODO Create a robot client instead of a GUI client
			Random rand = new Random();
			name = "Robot" + String.valueOf(rand.nextInt(9999)); // Generate a random name for robot
			main_client = new RobotClient(name, out);
			//this.addKeyListener((KeyListener) main_client);
			//maze.addClient();
		} else { // Case: GUI client
			// Create the GUIClient and connect it to the KeyListener queue
			main_client = new GUIClient(name, out);
			this.addKeyListener((KeyListener) main_client);
		}

		try {
			// Request server to add player and get the maze
			if (req_join(in, out, name, scoreModel) == false) {
				quit(1);
			}
			assert(maze != null && main_client.getID() > 0);
			
			// Find an initial position for itself and request the server to allocate that position
			// If GUIClient, then add client to maze now
			while (true) { // Keep trying until it finds an accepted initial position
				Packet req = new Packet(Packet.Type.INIT_POS, 2, main_client.getID());
				req.point = maze.getInitPoint();
				out.writeObject(req); // Send
				Packet reply = (Packet) in.readObject(); // Receive
				if (reply.cid == 0 && reply.type == Packet.Type.INIT_POS && reply.point != null) {
					if (maze.addClient(main_client, reply.point, null)) { // Finally adding the main client to the GUI
						break;
					}
				}
			}
			
			// After this point can expect to take part in listening and broadcasting etc.
			
			// Creating the receiver thread
			assert(maze instanceof MazeImpl);
			recvr = new LocalRecvrThread(clientSocket, in, (MazeImpl) maze, main_client);
			
		} catch (Exception e) {
			System.err.println(e.toString());
			e.printStackTrace();
			quit(1);
		}

		// Create the panel that will display the maze.
		overheadPanel = new OverheadMazePanel(maze, main_client);
		assert(overheadPanel != null);
		maze.addMazeListener(overheadPanel);
		
		// Starting the receiver thread
		recvr.start();

		// Don't allow editing the console from the GUI
		console.setEditable(false);
		console.setFocusable(false);
		console.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder()));

		// Allow the console to scroll by putting it in a scrollpane
		JScrollPane consoleScrollPane = new JScrollPane(console);
		assert(consoleScrollPane != null);
		consoleScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Console"));

		// Create the score table
		scoreTable = new JTable(scoreModel);
		assert(scoreTable != null);
		scoreTable.setFocusable(false);
		scoreTable.setRowSelectionAllowed(false);

		// Allow the score table to scroll too.
		JScrollPane scoreScrollPane = new JScrollPane(scoreTable);
		assert(scoreScrollPane != null);
		scoreScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Scores"));

		// Create the layout manager
		GridBagLayout layout = new GridBagLayout();
		GridBagConstraints c = new GridBagConstraints();
		getContentPane().setLayout(layout);

		// Define the constraints on the components.
		c.fill = GridBagConstraints.BOTH;
		c.weightx = 1.0;
		c.weighty = 3.0;
		c.gridwidth = GridBagConstraints.REMAINDER;
		layout.setConstraints(overheadPanel, c);
		c.gridwidth = GridBagConstraints.RELATIVE;
		c.weightx = 2.0;
		c.weighty = 1.0;
		layout.setConstraints(consoleScrollPane, c);
		c.gridwidth = GridBagConstraints.REMAINDER;
		c.weightx = 1.0;
		layout.setConstraints(scoreScrollPane, c);

		// Add the components
		getContentPane().add(overheadPanel);
		getContentPane().add(consoleScrollPane);
		getContentPane().add(scoreScrollPane);

		// Pack everything neatly.
		pack();

		// Let the magic begin.
		setVisible(true);
		overheadPanel.repaint();
		this.requestFocusInWindow();
	}


	/**
	 * Entry point for the game.  
	 * @param args Command-line arguments.
	 */
	public static void main(String args[]) {

		try {
			if (args.length == 2) {
				/* Create the GUI */
				new Mazewar(args[0], Integer.parseInt(args[1]));
			} else {
				System.err.println("Usage: Mazewar <server host> <server port>");
			}
			
			// Wait for the receiver to end
			recvr.join();
		} catch (NumberFormatException e) {
			System.err.println("Error: expected integer for port number");
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}


class LocalRecvrThread extends Thread {
	private Socket socket = null;
	private ObjectInputStream in = null;
	private boolean active = false;
	private MazeImpl maze = null;
	private Client main_client = null;

	public LocalRecvrThread(Socket socket, ObjectInputStream in, MazeImpl maze, Client main_client) {//TODO queue to be passed in
		super("LocalRecvrThread");
		this.socket = socket;
		this.in = in;
		this.maze = maze;
		this.main_client = main_client;
	}

	public void run() {
		active = true;
		System.out.println("LocalRecvrThread: running");
		while (active) {
			try {
				// Receive a packet
				Packet update = (Packet) in.readObject();
				
				// Check for JOIN packet case
				if (update.type == Packet.Type.JOIN) {
					if (!main_client.getName().equals(update.message)) {
						// Adding new client
						System.out.println("LocalRecvrThread: joining " + update.value);
						assert(update.value > 0 && update.message != null);
						Client new_player = new RemoteClient(update.message);
						new_player.setID(update.value);
						maze.addClient(new_player, update.point, null); //TODO Check if anything left to do
						continue;
					} else { // Its own JOIN announcement
						assert(update.value == main_client.getID()); // Check consistency
						// Ignored
						continue;
					}
				}
				
				// Try to get the relevant client
				Client performer = maze.getClient(update.message, update.value);
				if (performer == null) {
					System.err.println(this.getName() + ": missing client " + update.message + " " + update.value + " [CRITICAL]");
					//assert(true);
				}
				
				if (main_client.getName().equals(update.message)) { //RM
					System.out.println("LocalRecvrThread: my turn - " + update.type);
				}
				
				// Process the packet //TODO JOIN
				boolean status = true;
				switch (update.type) {
					// Action type update
					case UPDATE_ACTION:
						switch (update.update_type) {
						case FWD:
							status = performer.processEvent(ClientEvent.moveForward);
							break;
						case BACK:
							status = performer.processEvent(ClientEvent.moveBackward);
							break;
						case LEFT:
							performer.processEvent(ClientEvent.turnLeft);
							break;
						case RIGHT:	
							performer.processEvent(ClientEvent.turnRight);
							break;
						case FIRE:
							status = performer.processEvent(ClientEvent.fire);
							break;
						case QUIT:
							// Check if this client is the main or a remote client
							if (performer instanceof RemoteClient) {
								// Checks
								assert(performer.getID() != main_client.getID());
								assert(!performer.getName().equals(main_client.getName()));
							} else {
								// Checks
								assert(performer instanceof LocalClient);
								active = false; // Set to false since need to quit after this processing
							}
							if (!active) { // Means got our quit packet
								System.out.println("LocalRecvrThread: Finalized quit from server");
							}
							// If local main client, this call kills the main MazeWar thread
							status = performer.processEvent(ClientEvent.quit); // Probably won't return
							break;
						default:
							break;
						}
					
					case JOIN:
						if(performer.getID() == main_client.getID()) {
							// Asking to JOIN itself. Ignored
							assert(performer.getName().equals(main_client.getName()));
							break;
						}
						break;
					default:
						System.out.println("LocalRecvrThread: Invalid default command");
						break;
				}
				if (!status) {
					System.err.println(this.getName() + ": processing " + update.type + " was not successful.[CRITICAL]");
				}
				
			} catch (EOFException e) {
				if (!socket.isBound() || !socket.isConnected() || socket.isClosed() || socket.isInputShutdown() || socket.isOutputShutdown()) {
					System.out.println("LocalRecvrThread: Disconnected from server.");
				} else e.printStackTrace();
				active = false;
			} catch (IOException e) {
				if (!socket.isBound() || !socket.isConnected() || socket.isClosed() || socket.isInputShutdown() || socket.isOutputShutdown()) {
					System.out.println("LocalRecvrThread: Disconnected from server.");
				} else e.printStackTrace();
				active = false;
			} catch (ClassNotFoundException e) {
				active = false;
				e.printStackTrace();
			} catch (NullPointerException e) {
				System.err.println("LocalRecvrThread: Unexpected nullptr exception.");
				e.printStackTrace();
			} catch (Exception e) {
				System.err.println("LocalRecvrThread: Unhandeled exception.");
				e.printStackTrace();
			}
			
		}
	}
}
