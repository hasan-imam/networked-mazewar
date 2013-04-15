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

import java.lang.Thread;
import java.lang.Runnable;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.Random;
import java.util.Vector;  
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Collection;
import java.util.LinkedList;
import java.util.HashMap;

//Intermediate container
class Container_CDP implements Serializable {
	private static final long serialVersionUID = -4411384266865645933L;
	private int count = 0;
	public HashMap<String, Point> CM = new HashMap<String, Point>();
	
	public Container_CDP() {}
	
	public Container_CDP(HashMap<Client, DirectedPoint> CDP, ScoreTableModel score){
		System.out.println("In builder");
		//ScoreTableModel score =  maze.getScoreTable();
		for (Map.Entry<Client, DirectedPoint> entry : CDP.entrySet()) {
			// Marshal client ID + name + dir + packet_ID
		    String phrase = String.valueOf(entry.getKey().getID()) + ","; //ID
		    phrase += entry.getKey().getName() + ","; //name
		    phrase += String.valueOf(entry.getValue().getDirection().toInt()) + ",";//direction
		    phrase += String.valueOf(entry.getKey().get_pid().intValue()) + ","; //packet ID
		    
		    // Marshal client score
		    int points = Integer.MAX_VALUE;
		    if (score != null) {
		    	points = score.getScore(entry.getKey());
		    } 
		    if (points != Integer.MAX_VALUE) {
		    	phrase += String.valueOf(points);
		    } else {
		    	System.err.println("ERROR: Client score missing");
		    	phrase += String.valueOf(0);
		    }
		    
		    System.out.println(">" + phrase);
		    
		    // Tag and bag
		    CM.put(phrase, new Point(entry.getValue().getPoint()));
		    count++;
		}
	}
	
}


/**
 * A concrete implementation of a {@link Maze}.  
 * @author Geoffrey Washburn &lt;<a href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: MazeImpl.java 371 2004-02-10 21:55:32Z geoffw $
 */

public class MazeImpl extends Maze implements Serializable, ClientListener, Runnable {

        /**
         * Create a {@link Maze}.
         * @param point Treat the {@link Point} as a magnitude specifying the
         * size of the maze.
         * @param seed Initial seed for the random number generator.
         */
        public MazeImpl(Point point, long seed) {
                maxX = point.getX();
                assert(maxX > 0);
                maxY = point.getY();
                assert(maxY > 0);
                
                // Initialize the maze matrix of cells
                mazeVector = new Vector(maxX);
                for(int i = 0; i < maxX; i++) {
                        Vector colVector = new Vector(maxY);
                        
                        for(int j = 0; j < maxY; j++) {
                                colVector.insertElementAt(new CellImpl(), j);
                        }
                        
                        mazeVector.insertElementAt(colVector, i);
                }

                thread = new Thread(this);

                // Initialized the random number generator
                randomGen = new Random(42); //TODO
                
                // Build the maze starting at the corner
                buildMaze(new Point(0,0));

                thread.start();
        }
        
        public MazeImpl(ObjectInputStream s, long seed, ScoreTableModel scoreModel) throws IOException, ClassNotFoundException {
        	assert(s != null);
        	assert(scoreModel != null);
        	
        	// Setting ScoreTableModel
        	this.scoreModel = scoreModel;
			// Have the ScoreTableModel listen to the maze to find
			// out how to adjust scores.
			this.addMazeListener(scoreModel);
        	
        	//ObjectInputStream s = new ObjectInputStream(stream);
        	Point point = (Point) s.readObject();
        	maxX = point.getX();
        	maxY = point.getY();
        	
        	// Initialize the maze matrix of cells
            mazeVector = new Vector(maxX);
            for(int i = 0; i < maxX; i++) {
                    Vector colVector = new Vector(maxY);
                    
                    for(int j = 0; j < maxY; j++) {
                            colVector.insertElementAt(new CellImpl(), j);
                    }
                    
                    mazeVector.insertElementAt(colVector, i);
            }
        	
        	// Initialized the random number generator
        	randomGen = new Random(42); // MUST be the same seed as the server //TODO
            
        	// Get info on remote clients
        	Container_CDP temp = (Container_CDP) s.readObject();
        	
        	// Updating clientMap and related data by adding remote clients
        	for (Map.Entry<String, Point> entry : temp.CM.entrySet()) {
        		// Parsing
        		String phrase = entry.getKey();
        		String delims = ",";
        		String[] tokens = phrase.split(delims);
        		assert(tokens.length == 5);
        		
        	    // Restore remote clients in local maze
        	    // Create client
        	    Client client = new RemoteClient(tokens[1]);
        	    // Set it's ID
        	    client.setID(Integer.parseInt(tokens[0]));
        	    // Set it's packet_ID // NOT NEEDED NOW
        	    
        	    // Add it to the map, along with its position and direction
        	    this.addClient(client, entry.getValue(), new Direction(Integer.parseInt(tokens[2])));
        	    
        	    // Update it's score
        	    int score;
        	    if (tokens[4] == null || tokens[4].equals("0")) score = 0;
        	    else score = Integer.parseInt(tokens[4]);

        	    this.scoreModel.setClientScore(client, score);
        	}
        	
        	//clientMap.putAll((HashMap) s.readObject());
        	
        	//clientFired.addAll((HashSet <Client>) s.readObject());
        	//listenerSet.addAll((HashSet <MazeListener>) s.readObject());
        	//mazeVector = (Vector<Vector<CellImpl>>) s.readObject();
        	//projectileMap.putAll((HashMap<Projectile, DirectedPoint>) s.readObject());
        	
        	thread = new Thread(this);
        	
        	// Build the maze starting at the corner
            buildMaze(new Point(0,0));

            thread.start();
        }
        
        /** 
         * Serialize this {@link MazeImpl} and write it to a stream.
         * @param stream The OutputStream to write the serialized object to.
         * */
        public void writeMazeStream(ObjectOutputStream s)
                throws IOException {
                        assert(s != null);
                       // ObjectOutputStream s = new ObjectOutputStream(stream);
                        s.writeObject(new Point(this.maxX, this.maxY));
                        //s.writeObject(this.randomGen);
                        Container_CDP temp = new Container_CDP(clientMap, this.scoreModel);
                        //if (clientMap == null) System.out.println("sad :(");
                        s.writeObject(temp);
                        //s.writeObject((HashMap)clientMap);
//                        s.writeObject(this.clientFired);    
                        //s.writeObject(this.listenerSet);
                        //s.writeObject(this.mazeVector);
//                        s.writeObject(this.projectileMap);
                        
                        //s.flush();//XXX
                }
       
        /** 
         * Create a maze from a serialized {@link MazeImpl} object read from an input stream.
         * The input stream is wrapped with a ObjectInputStream for this purpose
         * @param stream The stream to read the serialized object from.
         * @return A reconstituted {@link MazeImpl}. 
         */
        public static Maze readMazeStream(InputStream stream)
                throws IOException, ClassNotFoundException {
                        assert(stream != null);
                        //FileInputStream in = new FileInputStream(mazefile);
                        ObjectInputStream s = new ObjectInputStream(stream);
                        Maze maze = (Maze) s.readObject();
                        return maze;
                }
        
        /** 
         * Create a maze from a serialized {@link MazeImpl} object written to a file.
         * @param mazefile The filename to load the serialized object from.
         * @return A reconstituted {@link MazeImpl}. 
         */
        public static Maze readMazeFile(String mazefile)
                throws IOException, ClassNotFoundException {
                        assert(mazefile != null);
                        FileInputStream in = new FileInputStream(mazefile);
                        ObjectInputStream s = new ObjectInputStream(in);
                        Maze maze = (Maze) s.readObject();
                        System.out.println("Mazefile: " + mazefile + " opened.");//RM
                        return maze;
                }
        
        /** 
         * Serialize this {@link MazeImpl} to a file.
         * @param mazefile The filename to write the serialized object to.
         * */
        public void save(String mazefile)
                throws IOException {
                        assert(mazefile != null);
                        FileOutputStream out = new FileOutputStream(mazefile);
                        ObjectOutputStream s = new ObjectOutputStream(out);
                        s.writeObject(this);
                        s.flush();
                }
       
        /** 
         * Display an ASCII version of the maze to stdout for debugging purposes.  
         */
        public void print() {
                for(int i = 0; i < maxY; i++) {
                        for(int j = 0; j < maxX; j++) {
                                CellImpl cell = getCellImpl(new Point(j,i));
                                if(j == maxY - 1) {
                                        if(cell.isWall(Direction.South)) {
                                                System.out.print("+-+");
                                        } else {
                                                System.out.print("+ +");
                                        }
                                } else {
                                        if(cell.isWall(Direction.South)) {
                                                System.out.print("+-");
                                        } else {
                                                System.out.print("+ ");
                                        }
                                }
                                
                        }	    
                        System.out.print("\n");
                        for(int j = 0; j < maxX; j++) {
                                CellImpl cell = getCellImpl(new Point(j,i));
                                if(cell.getContents() != null) {
                                        if(cell.isWall(Direction.West)) {
                                                System.out.print("|*");
                                        } else {
                                                System.out.print(" *");
                                        }
                                } else {
                                        if(cell.isWall(Direction.West)) {
                                                System.out.print("| ");
                                        } else {
                                                System.out.print("  ");
                                        }
                                }
                                if(j == maxY - 1) {
                                        if(cell.isWall(Direction.East)) {
                                                System.out.print("|");
                                        } else {
                                                System.out.print(" ");
                                        }
                                }
                        }
                        System.out.print("\n");
                        if(i == maxX - 1) {
                                for(int j = 0; j < maxX; j++) {
                                        CellImpl cell = getCellImpl(new Point(j,i));
                                        if(j == maxY - 1) {
                                                if(cell.isWall(Direction.North)) {
                                                        System.out.print("+-+");
                                                } else {
                                                        System.out.print("+ +");
                                                }
                                        } else {
                                                if(cell.isWall(Direction.North)) {
                                                        System.out.print("+-");
                                                } else {
                                                        System.out.print("+ ");
                                                }
                                        }		
                                }
                                System.out.print("\n");     
                        }   
                }
                
        }
       
        
        public boolean checkBounds(Point point) {
                assert(point != null);
                return (point.getX() >= 0) && (point.getY() >= 0) && 
                        (point.getX() < maxX) && (point.getY() < maxY);
        }
        
        public Point getSize() {
                return new Point(maxX, maxY);
        }
        
        public synchronized Cell getCell(Point point) {
                assert(point != null);
                return getCellImpl(point);
        }
        
        public synchronized void addClient(Client client) {
                System.err.println("WARNING: DEPRECATED FUNDTION 'void addClient(Client client)' BEING CALLED");
        		assert(client != null);
                // Pick a random starting point, and check to see if it is already occupied
                Point point = new Point(randomGen.nextInt(maxX),randomGen.nextInt(maxY));
                CellImpl cell = getCellImpl(point);
                // Repeat until we find an empty cell
                while(cell.getContents() != null) {
                        point = new Point(randomGen.nextInt(maxX),randomGen.nextInt(maxY));
                        cell = getCellImpl(point);
                } 
                assert(addClient(client, point, null));
        }
        
        public synchronized boolean addClient(Client client, Point point, Direction dir) {
                assert(client != null);
                assert(checkBounds(point));
                CellImpl cell = getCellImpl(point);
                if (cell.getContents() != null) { // There should be no content there
                	return false;
                }
                
                Direction d; 
                if (dir == null) {
                	// Starts facing North, unless otherwise specified
					d = Direction.East;
				} else d = dir;
                
				cell.setContents(client);
                clientMap.put(client, new DirectedPoint(point, d));
                client.registerMaze(this);
                client.addClientListener(this);
                update();
                notifyClientAdd(client);
                return true;
        }
        
        /**
         * Current implementation is static. 
         */
        public synchronized Point getInitPoint() {//TODO
        	int[] x = {0, 0, maxX-1, maxX-1, maxX/2, 0,     maxX/2};
        	int[] y = {0, maxY-1, 0, maxY-1, maxY/2, maxY/2,     0};
        	boolean found = false;
        	
        	Point point = null;
        	CellImpl cell = null;
        	
        	// Try one of the static points
        	for (int i = 0; i < y.length && !found; i++) {
				point = new Point(x[i],y[i]);
				cell = getCellImpl(point);
				if (cell.getContents() == null) {
					found = true;
				}
			}
        	if (found) {
        		return point;
        	}
        	
        	System.err.println("-- Assigning random point --");
        	
        	// Pick a random starting point, and check to see if it is already occupied
            point = new Point(randomGen.nextInt(maxX),randomGen.nextInt(maxY));
            cell = getCellImpl(point);
            // Repeat until we find an empty cell
            while(cell.getContents() != null) {
                    point = new Point(randomGen.nextInt(maxX),randomGen.nextInt(maxY));
                    cell = getCellImpl(point);
            } 
            return point;
        }
        
        public synchronized Point getClientPoint(Client client) {
                assert(client != null);
                Object o = clientMap.get(client);
                assert(o instanceof Point);
                return (Point)o;
        }
        
        public synchronized Direction getClientOrientation(Client client) {
                assert(client != null);
                Object o = clientMap.get(client);
                assert(o instanceof DirectedPoint);
                DirectedPoint dp = (DirectedPoint)o;
                return dp.getDirection();
        }
       
        public synchronized void removeClient(Client client) {
                assert(client != null);
                Object o = clientMap.remove(client); // Remove the client and get its position
                assert(o instanceof Point);
                Point point = (Point)o;
                CellImpl cell = getCellImpl(point); // Get the cell at the position
                cell.setContents(null); // Free the cell
                clientMap.remove(client); // Removing the client from this map again, to be sure??
                client.unregisterMaze(); // Unregistering this maze from client's internal data
                client.removeClientListener(this); // Removing this maze form client's internal listener list
                update(); // Let all the maze listener in listenerset know about the mazeupdate()
                notifyClientRemove(client); // Let all the maze listener in listenerset know about clientRemoved()
        }

        public synchronized boolean clientFire(Client client) {
                assert(client != null);
                // If the client already has a projectile in play
                // fail.
                if(clientFired.contains(client)) {
                        return false;
                }
                
                Point point = getClientPoint(client);
                Direction d = getClientOrientation(client);
                CellImpl cell = getCellImpl(point);
                
                /* Check that you can fire in that direction */
                if(cell.isWall(d)) {
                        return false;
                }
                
                DirectedPoint newPoint = new DirectedPoint(point.move(d), d);
                /* Is the point withint the bounds of maze? */
                assert(checkBounds(newPoint));
                
                CellImpl newCell = getCellImpl(newPoint);
                Object contents = newCell.getContents();
                if(contents != null) {
                        // If it is a Client, kill it outright
                        if(contents instanceof Client) {
                                notifyClientFired(client);
                                killClient(client, (Client)contents);
                                update();
                                return true; 
                        } else {
                        // Otherwise fail (bullets will destroy each other)
                                return false;
                        }
                }
                
                clientFired.add(client);
                Projectile prj = new Projectile(client);
                
                /* Write the new cell */
                projectileMap.put(prj, newPoint);
                newCell.setContents(prj);
                notifyClientFired(client);
                update();
                return true; 
        }
        
        public synchronized boolean moveClientForward(Client client) {
                assert(client != null);
                Object o = clientMap.get(client);
                assert(o instanceof DirectedPoint);
                DirectedPoint dp = (DirectedPoint)o;
                return moveClient(client, dp.getDirection());
        }
        
        public synchronized boolean moveClientBackward(Client client) {
                assert(client != null);
                Object o = clientMap.get(client);
                assert(o instanceof DirectedPoint);
                DirectedPoint dp = (DirectedPoint)o;
                return moveClient(client, dp.getDirection().invert());
        }
        
       
        public synchronized Iterator getClients() {
                return clientMap.keySet().iterator();
        }
        
        
        /**
         * Given a name and/or an ID of a client, finds the client in the maze's clientMap
         * Atleast one of the given parameters has to valid to find the client
         * If both parameter given and it finds a client that matches one or the other, it throws an exception
         * @param name Name of the client to search for. Should be null if not to be used
         * @param id Client ID. Should be -1 if not to be used
         * @return Returns the client that match the given information 
         */
        public synchronized Client getClient(String name, int id) throws Exception {
        	if (id == -1 && name == null) return null;
        	
        	//Iterator it = getClients();
            Iterator<Map.Entry<Client,DirectedPoint>> it = clientMap.entrySet().iterator();
        	while (it.hasNext()) {
                Map.Entry pairs = (Map.Entry)it.next();
                if (id == -1) { // Check by name
                	if (((Client)pairs.getKey()).getID() == id) {
                		return (Client) pairs.getKey();
                	}
                } else if (name == null) { // Check by ID
                	if (((Client)pairs.getKey()).getName().equals(name)) {
                		return (Client) pairs.getKey();
                	}
                } else { // Check by both attributes, since both valid
                	int temp_id = ((Client)pairs.getKey()).getID();
                	String temp_name = ((Client)pairs.getKey()).getName();
                	if (temp_id == id && temp_name.equals(name)) { // Both matched
                		return (Client) pairs.getKey();
                	} else if ((temp_id == id || temp_name.equals(name)) && (temp_id != -1 || !temp_name.equals(name))) { // Only one matched, unexpected
                		//} else if ((temp_id == -1 || temp_name.equals(name)) && (temp_id != -1 || !temp_name.equals(name))) {
                		// Only one matches but other doesnt. Inconsistency in clientMap
                		throw new Exception("the clientMap or a client of this MazeImpl is possibly inconsisten. Cause: Client name-id pair <" + temp_name + "," + temp_id + "> was unexpected.");
                	}
                }
            }
            
            return null;
        }
        
        /**
         * Returns the {@link ScoreTableModel} connected to the maze as a listener
         * @return ScorerTableModel if any, otherwise null
         */
        public ScoreTableModel getScoreTable() {
        	Iterator<MazeListener> itr = listenerSet.iterator();
        	while (itr.hasNext()) {
				MazeListener object = (MazeListener) itr.next();
				if (object instanceof ScoreTableModel) {
					return (ScoreTableModel) object;
				}
			}
        	return null;
        }
        
        public void addMazeListener(MazeListener ml) {
                listenerSet.add(ml);
        }

        public void removeMazeListener(MazeListener ml) {
                listenerSet.remove(ml);
        }

        /**
         * Listen for notifications about action performed by 
         * {@link Client}s in the maze.
         * @param c The {@link Client} that acted.
         * @param ce The action the {@link Client} performed.
         */
        public void clientUpdate(Client c, ClientEvent ce) {
                // When a client turns, update our state.
                if(ce == ClientEvent.turnLeft) {
                        rotateClientLeft(c);
                } else if(ce == ClientEvent.turnRight) {
                        rotateClientRight(c);
                }
        }

        /**
         * Control loop for {@link Projectile}s.
         */
        public void run() {
        		System.out.println("MazeImpl running");//RM
                Collection deadPrj = new HashSet();
                while(true) {
                        if(!projectileMap.isEmpty()) {
                                Iterator it = projectileMap.keySet().iterator();
                                synchronized(projectileMap) {
                                        while(it.hasNext()) {   
                                                Object o = it.next();
                                                assert(o instanceof Projectile);
                                                deadPrj.addAll(moveProjectile((Projectile)o));
                                        }               
                                        it = deadPrj.iterator();
                                        while(it.hasNext()) {
                                                Object o = it.next();
                                                assert(o instanceof Projectile);
                                                Projectile prj = (Projectile)o;
                                                projectileMap.remove(prj);
                                                clientFired.remove(prj.getOwner());
                                        }
                                        deadPrj.clear();
                                }
                        }
                        try {
                                thread.sleep(200); // Essentailly, timeout for projectile movement
                        } catch(Exception e) {
                                // shouldn't happen
                        }
                }
        }
        
        /* Internals */
        
        private synchronized Collection moveProjectile(Projectile prj) {
                Collection deadPrj = new LinkedList();
                assert(prj != null);
                
                Object o = projectileMap.get(prj);
                assert(o instanceof DirectedPoint);
                DirectedPoint dp = (DirectedPoint)o;
                Direction d = dp.getDirection();
                CellImpl cell = getCellImpl(dp);
                
                /* Check for a wall */
                if(cell.isWall(d)) {
                        // If there is a wall, the projectile goes away.
                        cell.setContents(null);
                        deadPrj.add(prj);
                        update();
                        return deadPrj;
                }
                
                DirectedPoint newPoint = new DirectedPoint(dp.move(d), d);
                /* Is the point within the bounds of maze? */
                assert(checkBounds(newPoint));
                
                CellImpl newCell = getCellImpl(newPoint);
                Object contents = newCell.getContents();
                if(contents != null) {
                        // If it is a Client, kill it outright
                        if(contents instanceof Client) {
                                killClient(prj.getOwner(), (Client)contents);
                                cell.setContents(null);
                                deadPrj.add(prj);
                                update();
                                return deadPrj;
                        } else {
                        // Bullets destroy each other
                                assert(contents instanceof Projectile);
                                newCell.setContents(null);
                                cell.setContents(null);
                                deadPrj.add(prj);
                                deadPrj.add(contents);
                                update();
                                return deadPrj;
                        }
                }

                /* Clear the old cell */
                cell.setContents(null);
                /* Write the new cell */
                projectileMap.put(prj, newPoint);
                newCell.setContents(prj);
                update();
                return deadPrj;
        }
  
        
        /**
         * Internal helper for handling the death of a {@link Client}.
         * @param source The {@link Client} that fired the projectile.
         * @param target The {@link Client} that was killed.
         */
        private synchronized void killClient(Client source, Client target) {
                assert(source != null);
                assert(target != null);
                Mazewar.consolePrintLn(source.getName() + " just vaporized " + 
                                target.getName());
                Object o = clientMap.remove(target);
                assert(o instanceof Point);
                Point point = (Point)o;
                CellImpl cell = getCellImpl(point);
                cell.setContents(null);
                // Pick a random starting point, and check to see if it is already occupied //TODO
                //point = new Point(randomGen.nextInt(maxX),randomGen.nextInt(maxY));
                point = getInitPoint();
                cell = getCellImpl(point);
                // Repeat until we find an empty cell
                while(cell.getContents() != null) {
                        //point = new Point(randomGen.nextInt(maxX),randomGen.nextInt(maxY));//TODO
                        point = getInitPoint();
                        cell = getCellImpl(point);
                }
                Direction d = Direction.South;
//                Direction d = Direction.random();
//                while(cell.isWall(d)) {
//                        d = Direction.random();
//                }
                cell.setContents(target);
                clientMap.put(target, new DirectedPoint(point, d));
                update();
                notifyClientKilled(source, target);
        }
        
        /**
         * Internal helper called when a {@link Client} emits a turnLeft action.
         * @param client The {@link Client} to rotate.
         */
        private synchronized void rotateClientLeft(Client client) {
                assert(client != null);
                Object o = clientMap.get(client);
                assert(o instanceof DirectedPoint);
                DirectedPoint dp = (DirectedPoint)o;
                clientMap.put(client, new DirectedPoint(dp, dp.getDirection().turnLeft()));
                update();
        }
        
        /**
         * Internal helper called when a {@link Client} emits a turnRight action.
         * @param client The {@link Client} to rotate.
         */
        private synchronized void rotateClientRight(Client client) {
                assert(client != null);
                Object o = clientMap.get(client);
                assert(o instanceof DirectedPoint);
                DirectedPoint dp = (DirectedPoint)o;
                clientMap.put(client, new DirectedPoint(dp, dp.getDirection().turnRight()));
                update();
        }
        
        /**
         * Internal helper called to move a {@link Client} in the specified
         * {@link Direction}.
         * @param client The {@link Client} to be move.
         * @param d The {@link Direction} to move.
         * @return If the {@link Client} cannot move in that {@link Direction}
         * for some reason, return <code>false</code>, otherwise return 
         * <code>true</code> indicating success.
         */
        private synchronized boolean moveClient(Client client, Direction d) {
                assert(client != null);
                assert(d != null);
                Point oldPoint = getClientPoint(client); // Get client's previous position
                CellImpl oldCell = getCellImpl(oldPoint); // Get what is there at that position
                
                /* Check that you can move in the given direction */
                if(oldCell.isWall(d)) {
                        /* Move failed */
                		assert(oldPoint instanceof DirectedPoint);
                        clientMap.put(client, (DirectedPoint)oldPoint);
                        return false;
                }
                
                DirectedPoint newPoint = new DirectedPoint(oldPoint.move(d), getClientOrientation(client));
                
                /* Is the point withint the bounds of maze? */
                assert(checkBounds(newPoint));
                CellImpl newCell = getCellImpl(newPoint);
                if(newCell.getContents() != null) {
                        /* Move failed */
                		assert(oldPoint instanceof DirectedPoint);
                    	clientMap.put(client, (DirectedPoint)oldPoint);
                        return false;
                }
                
                /* Write the new cell */
                clientMap.put(client, newPoint);
                newCell.setContents(client);
                /* Clear the old cell */
                oldCell.setContents(null);	
                
                update();
                return true; 
        }
        
        public synchronized boolean trymove(Client client, Direction d) {
            assert(client != null);
            assert(d != null);
            Point oldPoint = getClientPoint(client); // Get client's previous position
            CellImpl oldCell = getCellImpl(oldPoint); // Get what is there at that position
            
            /* Check that you can move in the given direction */
            if(oldCell.isWall(d)) {
                    /* Move failed */
            		assert(oldPoint instanceof DirectedPoint);
                    clientMap.put(client, (DirectedPoint)oldPoint);
                    return false;
            }
            
            return true; 
        }
       
        /**
         * The score manager for {@link Maze}.
         */
        public ScoreTableModel scoreModel;
        
        /**
         * The random number generator used by the {@link Maze}.
         */
        public final Random randomGen;

        /**
         * The maximum X coordinate of the {@link Maze}.
         */
        private final int maxX;

        /**
         * The maximum Y coordinate of the {@link Maze}.
         */
        private final int maxY;

        /** 
         * The {@link Vector} of {@link Vector}s holding the
         * {@link Cell}s of the {@link Maze}.
         */
        private final Vector mazeVector;

        /**
         * A map between {@link Client}s and {@link DirectedPoint}s
         * locating them in the {@link Maze}.
         */
        //private final Map clientMap = new HashMap();
        private final HashMap/*<Client, DirectedPoint>*/ clientMap = new HashMap();

        /**
         * The set of {@link MazeListener}s that are presently
         * in the notification queue.
         */
        private final HashSet/*<MazeListener>*/ listenerSet = new HashSet();

        /**
         * Mapping from {@link Projectile}s to {@link DirectedPoint}s. 
         */
        private final HashMap/*<Projectile, DirectedPoint>*/ projectileMap = new HashMap();
        
        /**
         * The set of {@link Client}s that have {@link Projectile}s in 
         * play.
         */
        private final HashSet/*<Client>*/  clientFired = new HashSet();
       
        /**
         * The thread used to manage {@link Projectile}s.
         */
        private final Thread thread;
        
        /**
         * Generate a notification to listeners that a
         * {@link Client} has been added.
         * @param c The {@link Client} that was added.
         */
        private void notifyClientAdd(Client c) {
                assert(c != null);
                Iterator i = listenerSet.iterator();
                while (i.hasNext()) {
                        Object o = i.next();
                        assert(o instanceof MazeListener);
                        MazeListener ml = (MazeListener)o;
                        ml.clientAdded(c);
                } 
        }
        
        /**
         * Generate a notification to listeners that a 
         * {@link Client} has been removed.
         * @param c The {@link Client} that was removed.
         */
        private void notifyClientRemove(Client c) {
                assert(c != null);
                Iterator i = listenerSet.iterator();
                while (i.hasNext()) {
                        Object o = i.next();
                        assert(o instanceof MazeListener);
                        MazeListener ml = (MazeListener)o;
                        ml.clientRemoved(c);
                } 
        }
        
        /**
         * Generate a notification to listeners that a
         * {@link Client} has fired.
         * @param c The {@link Client} that fired.
         */
        private void notifyClientFired(Client c) {
                assert(c != null);
                Iterator i = listenerSet.iterator();
                while (i.hasNext()) {
                        Object o = i.next();
                        assert(o instanceof MazeListener);
                        MazeListener ml = (MazeListener)o;
                        ml.clientFired(c);
                } 
        }
        
        /**
         * Generate a notification to listeners that a
         * {@link Client} has been killed.
         * @param source The {@link Client} that fired the projectile.
         * @param target The {@link Client} that was killed.
         */
        private void notifyClientKilled(Client source, Client target) {
                assert(source != null);
                assert(target != null);
                Iterator i = listenerSet.iterator();
                while (i.hasNext()) {
                        Object o = i.next();
                        assert(o instanceof MazeListener);
                        MazeListener ml = (MazeListener)o;
                        ml.clientKilled(source, target);
                } 
        }
        
        /**
         * Generate a notification that the {@link Maze} has 
         * changed in some fashion.
         */
        private void update() {
                Iterator i = listenerSet.iterator();
                while (i.hasNext()) {
                        Object o = i.next();
                        assert(o instanceof MazeListener);
                        MazeListener ml = (MazeListener)o;
                        ml.mazeUpdate();
                } 
        }

        /**
         * A concrete implementation of the {@link Cell} class that
         * special to this implementation of {@link Maze}s.
         */
        private class CellImpl extends Cell implements Serializable {
                /**
                 * Has this {@link CellImpl} been visited while
                 * constructing the {@link Maze}.
                 */
                private boolean visited = false;

                /**
                 * The walls of this {@link Cell}.
                 */
                private boolean walls[] = {true, true, true, true};

                /**
                 * The contents of the {@link Cell}. 
                 * <code>null</code> indicates that it is empty.
                 */
                private Object contents = null;
                
                /**
                 * Helper function to convert a {@link Direction} into 
                 * an array index for easier access.
                 * @param d The {@link Direction} to convert.
                 * @return An integer index into <code>walls</code>.
                 */
                private int directionToArrayIndex(Direction d) {
                        assert(d != null);
                        if(d.equals(Direction.North)) {
                                return 0;
                        } else if(d.equals(Direction.East)) {
                                return 1;
                        } else if(d.equals(Direction.South)) {
                                return 2;
                        } else if(d.equals(Direction.West)) {
                                return 3;
                        }
                        /* Impossible */
                        return -1; 
                }
                
                /* Required for the abstract implementation */
                
                public boolean isWall(Direction d) {
                        assert(d != null);
                        return this.walls[directionToArrayIndex(d)];
                }
                
                public synchronized Object getContents() {
                        return this.contents;
                }
                
                /* Internals used by MazeImpl */
                
                /**
                 * Indicate that this {@link Cell} has been
                 * visited while building the {@link MazeImpl}.
                 */
                public void setVisited() {
                        visited = true;
                }
                
                /**
                 * Has this {@link Cell} been visited in the process
                 * of recursviely building the {@link Maze}?
                 * @return <code>true</code> if visited, <code>false</code> 
                 * otherwise.
                 */
                public boolean visited() {
                        return visited;
                }
                
                /**
                 * Add a wall to this {@link Cell} in the specified
                 * Cardinal {@link Direction}.
                 * @param d Which wall to add.
                 */
                public void setWall(Direction d) {
                        assert(d != null);
                        this.walls[directionToArrayIndex(d)] = true;
                }
                
                /**
                 * Remove the wall from this {@link Cell} in the specified
                 * Cardinal {@link Direction}.
                 * @param d Which wall to remove.
                 */
                public void removeWall(Direction d) {
                        assert(d != null);
                        this.walls[directionToArrayIndex(d)] = false;
                }
                
                /**
                 * Set the contents of this {@link Cell}.
                 * @param contents Object to place in the {@link Cell}.
                 * Use <code>null</code> if you want to empty it.
                 */
                public synchronized void setContents(Object contents) {
                        this.contents = contents;
                }
                
        }
        
        /** 
         * Removes the wall in the {@link Cell} at the specified {@link Point} and 
         * {@link Direction}, and the opposite wall in the adjacent {@link Cell}.
         * @param point Location to remove the wall.
         * @param d Cardinal {@link Direction} specifying the wall to be removed.
         */
        private void removeWall(Point point, Direction d) {
                assert(point != null);
                assert(d != null);
                CellImpl cell = getCellImpl(point);
                cell.removeWall(d);
                Point adjacentPoint = point.move(d);
                CellImpl adjacentCell = getCellImpl(adjacentPoint);
                adjacentCell.removeWall(d.invert());
        }
        
        /** 
         * Pick randomly an unvisited neighboring {@link CellImpl}, 
         * if none return <code>null</code>. 
         * @param point The location to pick a neighboring {@link CellImpl} from.
         * @return The Cardinal {@link Direction} of a {@link CellImpl} that hasn't
         * yet been visited.
         */
        private Direction pickNeighbor(Point point) {
                assert(point != null);
                Direction directions[] = { 
                        Direction.North, 
                        Direction.East, 
                        Direction.West, 
                        Direction.South };
                        
                        // Create a vector of the possible choices
                        Vector options = new Vector();	       
                        
                        // Iterate through the directions and see which
                        // Cells have been visited, adding those that haven't
                        for(int i = 0; i < 4; i++) {
                                Point newPoint = point.move(directions[i]);
                                if(checkBounds(newPoint)) {
                                        CellImpl cell = getCellImpl(newPoint);
                                        if(!cell.visited()) {
                                                options.add(directions[i]);
                                        }
                                }
                        }
                        
                        // If there are no choices just return null
                        if(options.size() == 0) {
                                return null;
                        }
                        
                        // If there is at least one option, randomly choose one.
                        int n = randomGen.nextInt(options.size());
                        
                        Object o = options.get(n);
                        assert(o instanceof Direction);
                        return (Direction)o;
        }
        
        /**
         * Recursively carve out a {@link Maze}
         * @param point The location in the {@link Maze} to start carving.
         */
        private void buildMaze(Point point) {
                assert(point != null);
                CellImpl cell = getCellImpl(point);
                cell.setVisited();
                Direction d = pickNeighbor(point);
                while(d != null) {	    
                        removeWall(point, d);
                        Point newPoint = point.move(d);
                        buildMaze(newPoint);
                        d = pickNeighbor(point);
                }
        }
       
        /** 
         * Obtain the {@link CellImpl} at the specified point. 
         * @param point Location in the {@link Maze}.
         * @return The {@link CellImpl} representing that location.
         */
        private CellImpl getCellImpl(Point point) {
                assert(point != null);
                Object o1 = mazeVector.get(point.getX());
                assert(o1 instanceof Vector);
                Vector v1 = (Vector)o1;
                Object o2 = v1.get(point.getY());
                assert(o2 instanceof CellImpl);
                return (CellImpl)o2;
        }
}
