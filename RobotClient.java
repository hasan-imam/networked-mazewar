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

import java.util.Random;
import java.util.Vector;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.Runnable;

/**
 * A very naive implementation of a computer controlled {@link LocalClient}.  Basically
 * it stumbles about and shoots.  
 * @author Geoffrey Washburn &lt;<a href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: RobotClient.java 345 2004-01-24 03:56:27Z geoffw $
 */

public class RobotClient extends LocalClient implements Runnable {
	
	/**
	 * Stream that will be used to send event packets to the server
	 */
	private ObjectOutputStream out = null; 
	
	/**
	 * Random number generator so that the robot can be
	 * "non-deterministic".
	 */ 
	private final Random randomGen = new Random();

	/**
	 * The {@link Thread} object we use to run the robot control code.
	 */
	private final Thread thread;

	/** 
	 * Flag to say whether the control thread should be
	 * running.
	 */
	private boolean active = false;

	private final int frequency = 300;

	/**
	 * Create a computer controlled {@link LocalClient}.
	 * @param name The name of this {@link RobotClient}.
	 */
	public RobotClient(String name, ObjectOutputStream out) {
		super(name);
		assert(name != null && out != null);
		this.out = out;
		// Create our thread
		thread = new Thread(this);
	}

	/** 
	 * Override the abstract {@link Client}'s registerMaze method so that we know when to start 
	 * control thread.
	 * @param maze The {@link Maze} that we are begin registered with.
	 */
	public synchronized void registerMaze(Maze maze) {
		assert(maze != null);
		super.registerMaze(maze);

		// Get the control thread going.
		active = true;
		thread.start();
	}

	/** 
	 * Override the abstract {@link Client}'s unregisterMaze method so we know when to stop the 
	 * control thread. 
	 */
	public synchronized void unregisterMaze() {
		// Signal the control thread to stop
		active = false; 
		// Wait half a second for the thread to complete.
		try {
			thread.join(500);
		} catch(Exception e) {
			// Shouldn't happen
		}
		super.unregisterMaze();
	}

	/** 
	 * This method is the control loop for an active {@link RobotClient}. 
	 */
	public void run() {
		// Both sides are still setting things up when this starts, so give it some time bofore starting action
		try {
			Thread.sleep(2000);
		} catch(Exception e) {
			// Shouldn't happen.
		}
		
		// Put a spiffy message in the console
		Mazewar.consolePrintLn("Robot client \"" + this.getName() + "\" activated.");

		// Loop while we are active
		while(active) {
			// Packet sent for any type of event. So create the packet first
			Packet event = null;
			
			// Quit after playing 2000 steps (on average, assuming uniformed probability)
			int rand = randomGen.nextInt(1000000);
			if (rand == 1) {
				event = new Packet(Packet.Type.QUIT, use_packet().intValue(), getID());
				dispatcher(event);
				try {
					thread.sleep(5000); // Give server enough time to respond, in case it's busy
				} catch (InterruptedException e) {
					System.out.println(this.getName() + ": Interrupted sleep.");
					//e.printStackTrace();
				} 
				continue;
			}
			
			rand = randomGen.nextInt(4);
			switch (rand) {
			case 0:
				event = new Packet(Packet.Type.FWD, use_packet().intValue(), getID());
				break;

			case 1:
				if (randomGen.nextInt(3) == 1) {
					event = new Packet(Packet.Type.RIGHT, use_packet().intValue(), getID());
					break;
				} else {
					event = new Packet(Packet.Type.RIGHT, use_packet().intValue(), getID());
					break;
				}
				
			case 2:
				event = new Packet(Packet.Type.BACK, use_packet().intValue(), getID());
				break;
				
			case 3:
				if(randomGen.nextInt(3) == 1) {
					event = new Packet(Packet.Type.FIRE, use_packet().intValue(), getID());
				}
				break;
				
			default:
				// should not occur
				continue;
			}
			assert(event != null);
			
			// Request action
			if (event != null) {
				dispatcher(event);
			}
			
			// Sleep so the humans can possibly compete.
			try {
				Thread.sleep(frequency);
			} catch(Exception e) {
				// Shouldn't happen.
			}
		}
	}
	
	private void dispatcher (Packet event) {
		assert(event != null && out != null);
		
		// Send request to server
		try{
        	System.out.println(this.getName() + ": requesting action " + event.type);
			out.writeObject(event);
        } catch (IOException ex){
        	System.err.println(this.getName() + ": Failed to send movement request. IO error");
            ex.printStackTrace();
        } catch (NullPointerException ex) {
        	System.err.println(this.getName() + ": Failed to send movement request. Nullpointer");
        } catch (Exception ex) {
        	System.err.println(this.getName() + ": Failed to send movement request. Unknown error");
            ex.printStackTrace();
        }
	}
}
