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
  
import java.io.Serializable;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;

/**
 * An abstract class for clients in a maze. 
 * @author Geoffrey Washburn &lt;<a href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: Client.java 343 2004-01-24 03:43:45Z geoffw $
 */
public abstract class Client {
		
		/**
		 * ID of the client. 
		 * Note: The initial value of this member affects the setID(int id){...} implementation and should be set to -1 initially
		 */
        protected int client_id = -1;
        
        /**
         * Integer that stores the most recently sent packet's number for this client
         * Initialized to 2 since client sends 0,1,2 packets during startup
         */
        protected Integer packet_id = new Integer(2);
        
        /**
         *  Increments the packet_id of this client
         *  To be used when trying to get a new packet_id for the client
         *  @return An Integer that is usable as packet_id to send a packet from this client
         */
        public Integer use_packet() {
        	packet_id = new Integer(packet_id.intValue() + 1);
        	return packet_id;
        }
        
        public Integer get_pid(){
        	return packet_id;
        }
        
		/**
         * Register this {@link Client} as being contained by the specified
         * {@link Maze}.  Naturally a {@link Client} cannot be registered with
         * more than one {@link Maze} at a time.
         * @param maze The {@link Maze} in which the {@link Client} is being
         * placed.
         */
        public void registerMaze(Maze maze) {
                assert(maze != null);
                assert(this.maze == null);
                this.maze = maze;
        }

        /**
         * Inform the {@link Client} that it has been taken out of the {@link Maze}
         * in which it is located.  The {@link Client} must already be registered
         * with a {@link Maze} before this can be called.
         */
        public void unregisterMaze() {
                assert(maze != null);
                this.maze = null;
        }
        
        /**
         * Get the name of this {@link Client}.
         * @return A {@link String} naming the {@link Client}.
         */
        public String getName() {
                return name;
        }
        
        /**
         * Get the ID of this {@link Client}.
         * @return An {@link int} that is the ID of the {@link Client}.
         */
        public int getID() {
                return client_id;
        }
        
        /** 
         * Sets the ID of a client {@link Client} when the function is called the first time
         * The later invocations of this function is pointless and will not result in any change in the ID
         * @param client_id The ID of this {@link Client}.
         * @return Returns 0 if successful (first time invocation), or -1 for failed attempts 
         */
        public int setID(int id){
        	if (this.client_id <= 0) { // ID not yet initialized
        		this.client_id = id;
        		return 0;
        	} else { // ID already initialized
        		return -1;
        	}
        }
      
        /**
         * Obtain the location of this {@link Client}.
         * @return A {@link Point} specifying the location of the {@link Client}. 
         */
        public Point getPoint() {
                assert(maze != null);
                return maze.getClientPoint(this);
        }
        
        /**
         * Find out what direction this {@link Client} is presently facing.
         * @return A Cardinal {@link Direction}.
         */
        public Direction getOrientation() {
                assert(maze != null);
                return maze.getClientOrientation(this);
        }
       
        /**
         * Add an object to be notified when this {@link Client} performs an 
         * action.
         * @param cl An object that implementing the {@link ClientListener cl}
         * interface.
         */
        public void addClientListener(ClientListener cl) {
                assert(cl != null);
                listenerSet.add(cl);
        }
        
        /**
         * Remove an object from the action notification queue.
         * @param cl The {@link ClientListener} to remove.
         */
        public void removeClientListener(ClientListener cl) {
                listenerSet.remove(cl);
        }
        
        /* Internals ******************************************************/        
        
        /**
         * The maze where the client is located.  <code>null</code> if not
         * presently in a maze.
         */
        protected Maze maze = null;

        /**
         * Maintain a set of listeners.
         */
        private Set listenerSet = new HashSet();
        
        /**
         * Name of the client.
         */
        private String name = null;
       
        /** 
         * Create a new client with the specified name.
         */
        protected Client(String name) {
                assert(name != null);
                this.name = name;
                this.client_id = -1;
        }

        /**
         * Move the client forward.
         * @return <code>true</code> if move was successful, otherwise <code>false</code>.
         */
        protected boolean forward() {
                assert(maze != null);
                
                if(maze.moveClientForward(this)) {
                        notifyMoveForward();
                        return true;
                } else {
                        return false;
                }
        }
        
        /**
         * Move the client backward.
         * @return <code>true</code> if move was successful, otherwise <code>false</code>.
         */
        protected boolean backup() {
                assert(maze != null);

                if(maze.moveClientBackward(this)) {
                        notifyMoveBackward();
                        return true;
                } else {
                        return false;
                }
        }
        
        /**
         * Turn the client ninety degrees counter-clockwise.
         */
        protected void turnLeft() {
                notifyTurnLeft();
        }
        
        /**
         * Turn the client ninety degrees clockwise.
         */
        protected void turnRight() {
                notifyTurnRight();
        }
        
        /**
         * Fire a projectile.
         * @return <code>true</code> if a projectile was successfully launched, otherwise <code>false</code>.
         */
        protected boolean fire() {
                assert(maze != null);

                if(maze.clientFire(this)) {
                        notifyFire();
                        return true;
                } else {
                        return false;
                }
        }
        
        /**
         * Quit game. Removes this client from the maze.
         * Should be called after server has broadcasted that it should quit
         * Should be called by derived class' wrapper (i.e. remote or local clients)
         */
        protected void quit() {
        	assert(maze != null);
        	maze.removeClient(this);
        }
        
        /** 
         * Notify listeners that the client moved forward.
         */
        private void notifyMoveForward() {
                notifyListeners(ClientEvent.moveForward);
        }
        
        /**
         * Notify listeners that the client moved backward.
         */
        private void notifyMoveBackward() {
                notifyListeners(ClientEvent.moveBackward);
        }
        
        /**
         * Notify listeners that the client turned right.
         */
        private void notifyTurnRight() {
                notifyListeners(ClientEvent.turnRight);
        }
        
        /**
         * Notify listeners that the client turned left.
         */
        private void notifyTurnLeft() {
                notifyListeners(ClientEvent.turnLeft);       
        }
        
        /**
         * Notify listeners that the client fired.
         */
        private void notifyFire() {
                notifyListeners(ClientEvent.fire);       
        }
        
        /**
         * Send a the specified {@link ClientEvent} to all registered listeners
         * @param ce Event to be sent.
         */
        private void notifyListeners(ClientEvent ce) {
                assert(ce != null);
                Iterator i = listenerSet.iterator();
                while (i.hasNext()) {
                        Object o = i.next();
                        assert(o instanceof ClientListener);
                        ClientListener cl = (ClientListener)o;
                        cl.clientUpdate(this, ce);
                } 
        }
        
        /**
         * Processes the given {@link ClientEvent}
         * @param ce Client event to be processed
         * @return Status of the operation. Not important for turn actions
         */
        public abstract boolean processEvent(ClientEvent ce);
}
