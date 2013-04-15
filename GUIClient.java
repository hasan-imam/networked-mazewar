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

import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.ObjectOutputStream;

/**
 * An implementation of {@link LocalClient} that is controlled by the keyboard
 * of the computer on which the game is being run.  
 * @author Geoffrey Washburn &lt;<a href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: GUIClient.java 343 2004-01-24 03:43:45Z geoffw $
 */

public class GUIClient extends LocalClient implements KeyListener {

	/**
	 * Stream that will be used to send key event packets to the server
	 */
	private ObjectOutputStream out = null; 

	/**
	 * Create a GUI controlled {@link LocalClient}.  
	 */
	public GUIClient(String name, ObjectOutputStream _out) {
		super(name);
		out = _out;
	}

	/**
	 * Handle a key press by sending update request to server
	 * @param e The {@link KeyEvent} that occurred.
	 */
	public void keyPressed(KeyEvent e) {
		// Packet sent for any type of key event. So create the packet first
		Packet event = null;

		// Create event packet according to key event
		// If the user pressed Q, invoke the cleanup code and quit. 
		if((e.getKeyChar() == 'q') || (e.getKeyChar() == 'Q')) {
			event = new Packet(Packet.Type.QUIT, use_packet().intValue(), getID());
		// Up-arrow moves forward.
		} else if(e.getKeyCode() == KeyEvent.VK_UP) {
			event = new Packet(Packet.Type.FWD, use_packet().intValue(), getID());
		// Down-arrow moves backward.
		} else if(e.getKeyCode() == KeyEvent.VK_DOWN) {
			event = new Packet(Packet.Type.BACK, use_packet().intValue(), getID());
		// Left-arrow turns left.
		} else if(e.getKeyCode() == KeyEvent.VK_LEFT) {
			event = new Packet(Packet.Type.LEFT, use_packet().intValue(), getID());
		// Right-arrow turns right.
		} else if(e.getKeyCode() == KeyEvent.VK_RIGHT) {
			event = new Packet(Packet.Type.RIGHT, use_packet().intValue(), getID());
		// Spacebar fires.
		} else if(e.getKeyCode() == KeyEvent.VK_SPACE) {
			event = new Packet(Packet.Type.FIRE, use_packet().intValue(), getID());
		} else { // Unhandeled key event. Simply ignored
			return;
		}
		assert(event != null);
		
		// Send request to server
		try{
        	System.out.println(this.getName() + ": requesting action " + event.type);
			out.writeObject(event);
        } catch (IOException ex){
        	System.err.println("GUIClient: Failed to send movement request.");
            ex.printStackTrace();
        } catch (Exception ex) {
        	System.err.println("GUIClient: Failed to send movement request");
            ex.printStackTrace();
        }
	}

	/**
	 * Handle a key release. Not needed by {@link GUIClient}.
	 * @param e The {@link KeyEvent} that occurred.
	 */
	public void keyReleased(KeyEvent e) {
	}

	/**
	 * Handle a key being typed. Not needed by {@link GUIClient}.
	 * @param e The {@link KeyEvent} that occurred.
	 */
	public void keyTyped(KeyEvent e) {
	}

}
