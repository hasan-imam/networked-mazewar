import java.io.Serializable;

/**
 * A standard packet to be used for communication among the client(s) and the server (if any)
 * @author Hasan Imam
 */
public class Packet implements Serializable {
	/**
     * Possible packet types
     */
	public enum Type {FWD, BACK, RIGHT, LEFT, FIRE, // Action request packet
					  MOVE_REQ, // Player is requesting to move to specified field: NOT USED
					  UPDATE_ACTION, // Action update packet: Sent from server to all listeners as map updates
					  MAZE_REQ, // Sent from client when it wants to receive the whole maze from server.
					  INIT_POS, // Sent when the client requests an initial point for itself. Use for broadcasting as well
					  POS_ERROR, // Sent when requested Point is already occupied
					  JOIN, QUIT, REQ, REPLY, NULL}; // Other logistics
	
	// These are private since they wont change after creation and their values are known when they are created
	public Type type = Type.NULL;
	public int pid = -1; // Packet ID
	public int cid = -1; // Client ID 
	
	// Other message fields
	public DirectedPoint pos = null; // The new position where the client wants to move to
									 // Only applies for MOVE type packets
	
	public Maze maze = null; // Only used when server sending the whole maze to new client
	
	public int value = -1; // Used for multiple purposes. Generic int value container
	
	public Point point = null; // Used when someone needs to request a new position for themselves or the server broadcasts a client update
	
	public String message = null;
	
	public Type update_type = Type.NULL; // Used by server when broadcasting client updates
	
	/**
     * Create a {@link Packet}.
     * @param type The type of this {@link Packet}.
     * @param pid Packet ID
     * @param cid Client ID
     */
	public Packet(Type type, int pid, int cid) {
		this.type = type;
		this.pid = pid;
		this.cid = cid;
	}
	
	
}
