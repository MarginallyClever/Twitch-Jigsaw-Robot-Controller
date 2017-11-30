package com.TwitchJigsawRobotController;

import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.jibble.pircbot.*;

public class TwitchJigsawRobotController 
extends PircBot
implements ActionListener, PropertyChangeListener  {
	static public String nameRoot = "jigsolve";
    static public String CHANNEL = "#jigsolve";
    static public String NETWORK = "irc.chat.twitch.tv";
    static public int PORT = 6667;
    static public String PASSWORD = "oauth:j1xd8duzvlhuov9yczcrxgmo1eym3m";
	static public long ANNOUNCE_DELAY = 120000; // ms
    
	private XCarveInterface XCarve;
    private AddonInterface Addon;
    private String IPAddress;
    private Timer announcementTimer;
    
    protected JFrame frame;
    protected JPanel panel;
	protected JButton bNorth,bSouth,bEast,bWest,bPick,bDrop,bLeft,bRight;
    
	private long lastMove;
    private boolean dropOnce;

    
	public static void main(String[] argv) throws Exception {
		TwitchJigsawRobotController controller = new TwitchJigsawRobotController();
		
		controller.run();
	}
	
	
	public TwitchJigsawRobotController() {
		XCarve = new XCarveInterface();
		Addon = new AddonInterface();
	}
	
	
	public void run() throws Exception {
		XCarve.connect();
		Addon.connect();
		connectToIRC();
		getIPAddress();
		setupAnnouncementTimer();
		setupDialog();
		lastMove=System.currentTimeMillis();
	}
	
	
	protected void setupDialog() {
		frame = new JFrame("Jigsaw Controller");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		panel = new JPanel();
		panel.setLayout(new GridLayout(3,3));
		frame.setMinimumSize(new Dimension(300,300));

		bNorth = new JButton("North");
		bSouth = new JButton("South");
		bWest  = new JButton("West");
		bEast  = new JButton("East");
		bPick  = new JButton("Pick");
		bDrop  = new JButton("Drop");
		bLeft  = new JButton("CCW");
		bRight = new JButton("CW");

		panel.add(bLeft);
		panel.add(bNorth);
		panel.add(bRight);
		panel.add(bWest);
		panel.add(new JButton(" "));
		panel.add(bEast);
		panel.add(bPick);
		panel.add(bSouth);
		panel.add(bDrop);
		
		bNorth.addActionListener(this);
		bSouth.addActionListener(this);
		bWest.addActionListener(this);
		bEast.addActionListener(this);
		bPick.addActionListener(this);
		bDrop.addActionListener(this);
		bLeft.addActionListener(this);
		bRight.addActionListener(this);

		frame.setContentPane(panel);
		frame.pack();
		frame.setVisible(true);

	}
	
	
	protected void setupAnnouncementTimer() {
		System.out.println("Starting timer...");
		announcementTimer = new Timer();
		announcementTimer.schedule(new AnnounceWhere(), 0, ANNOUNCE_DELAY);
	}
	
	
	public class AnnounceWhere extends TimerTask {
		public void run() {
			// don't spam the channel
			if(System.currentTimeMillis()<lastMove+ANNOUNCE_DELAY) {
				// announce the location of the robot
				where(CHANNEL);
				dropOnce=true;
			} else if(dropOnce==true) {
				System.out.println("Automated sanity drop");
				drop();
				dropOnce=false;
			}
		}
	};
	
	protected void getIPAddress() {
		System.out.println("Finding IP Address...");
		IPAddress="unresolved";
		
		URL url;
		try {
			url = new URL("http://checkip.amazonaws.com/");
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return;
		}
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(url.openStream()));
			IPAddress= br.readLine();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		System.out.println("AWS says my IP is "+IPAddress);
	}

	
	protected void connectToIRC() throws Exception {
        // Enable debugging output.
        setVerbose(true);
        boolean nameInUse;
        String extra="";
        int extraNum=0;
        do {
        	nameInUse=false;
	        setName(nameRoot+extra);
	        
	        // Connect to the IRC server.
	        try {
	        	connect(NETWORK,PORT,PASSWORD);
	        	sendRawLine("CAP REQ :twitch.tv/commands");
	        }
	        catch(NickAlreadyInUseException e) {
	        	nameInUse=true;
	        	extraNum++;
	        	extra = (new Integer(extraNum)).toString();
	        	System.out.println("Named used.  Trying "+nameRoot+extra);
	        }
	        catch(IrcException e) {
	        	e.printStackTrace();
	        	throw e;
	        }
	        catch(IOException e) {
	        	e.printStackTrace();
	        	throw e;
	        }
        } while(nameInUse);

        joinChannel(CHANNEL);
        System.out.println("Connected to IRC "+NETWORK+CHANNEL);
	}
	
	
    // When a message is received from IRC
	@Override
    public void onMessage(String channel, String sender,
                       String login, String hostname, String message) {
		boolean ignore=false;
		
    	message = message.toUpperCase();
    	     if(message.equalsIgnoreCase("N")) north();
    	else if(message.equalsIgnoreCase("S")) south();
    	else if(message.equalsIgnoreCase("E")) east();
    	else if(message.equalsIgnoreCase("W")) west();
    	else if(message.equalsIgnoreCase("P")) pick();
    	else if(message.equalsIgnoreCase("D")) drop();
    	else if(message.equalsIgnoreCase("L")) left();
    	else if(message.equalsIgnoreCase("R")) right();
    	else if(message.equalsIgnoreCase("NORTH")) north();
    	else if(message.equalsIgnoreCase("SOUTH")) south();
    	else if(message.equalsIgnoreCase("EAST")) east();
    	else if(message.equalsIgnoreCase("WEST")) west();
    	else if(message.equalsIgnoreCase("PICK")) pick();
    	else if(message.equalsIgnoreCase("DROP")) drop();
    	else if(message.equalsIgnoreCase("LEFT")) left();
    	else if(message.equalsIgnoreCase("RIGHT")) right();
    	else if(message.equalsIgnoreCase("CCW")) left();
    	else if(message.equalsIgnoreCase("CW")) right();
    	//else if(message.equalsIgnoreCase("!commands")) commands(sender);
    	//else if(message.equalsIgnoreCase("!command")) commands(sender);
    	//else if(message.equalsIgnoreCase("!help")) help(sender);
    	//else if(message.equalsIgnoreCase("!about")) help(sender);
    	else if(message.equalsIgnoreCase("!where")) where(sender);
    	else ignore=parseGo(message);;
    	// else ignore
    	     
    	if(ignore==false) {
    		lastMove=System.currentTimeMillis();
    	}
    }
    
	/**
	 * Parse messages in the format "GO [Xnnn] [Ynnn]".  nnn is a float.  X and Y parts are optional.
	 * @param message the string sent from the 
	 * @return true if message was ignored.
	 */
	protected boolean parseGo(String message) {
		String command="GO";
		if(!message.startsWith(command))
			return true;  // does not start with right command.

		boolean ignore=false;
		float newX = XCarve.getX();
		float newY = XCarve.getY();
		float newA = Addon.getAngleDegrees();

		StringTokenizer st = new StringTokenizer(message);
		while(st.hasMoreTokens()) {
			String tok=st.nextToken();
			
			if(tok.startsWith("X")) {
				newX = Float.parseFloat(tok.substring(1));
			} else if(tok.startsWith("Y")) {
				newY = Float.parseFloat(tok.substring(1));
			} else if(tok.startsWith("A")) {
				newA = Float.parseFloat(tok.substring(1));
			} else if(tok.equals(command)){
			} else {
				// badly formed commands are ignored, even if some parts are OK.
				ignore=true;
			}
		}
		
		if(ignore==false) {
			if(!XCarve.isInBounds(newX, newY)) {
				sendMessage(CHANNEL,""+newX +", "+newY+" is out of bounds.");
			} else {
				sendMessage(CHANNEL,"Moving to X"+newX +" Y"+newY+"...");
				XCarve.moveAbsolute(newX, newY);
			}
			if(newA != Addon.getAngleDegrees()) {
				sendMessage(CHANNEL,"Turning to A"+newA);
				Addon.turnAbsolute(newA);
			}
		}
		return ignore;
	}
	
    // https://discuss.dev.twitch.tv/t/sending-whispers-with-my-irc-bot/4346/20
    protected void privMessage(String sender,String msg) {
    	this.sendRawLineViaQueue("PRIVMSG "+CHANNEL+" :/w "+sender+" "+msg);
    }
    
    protected void commands(String sender) {
    	privMessage(sender,"N/E/S/W=move L/R=turn P=pick D=drop !where !about !commands");
    }
    
    protected void help(String sender) {
    	privMessage(sender,"Marginallyclever.com's collaborative jigsaw robot.  Find out more on our website.  Show your support with a like, share, or reblog!");
    }

    protected void where(String sender) {
		// report machine state here
		String arg1 = /*IPAddress+":12345 "+*/ XCarve.where() + " " + Addon.where();
		if(sender.equals(CHANNEL)) sendMessage(CHANNEL,arg1);
		else privMessage(sender, arg1);
    }
    
    protected void north() {
    	XCarve.north();
    }
    protected void south() {
    	XCarve.south();
    }
    protected void east() {
    	XCarve.east();
    }
    protected void west() {
    	XCarve.west();
    }
    protected void left() {
    	Addon.turnRight();
    }
    protected void right() {
    	Addon.turnLeft();
    }
    protected void drop() {
		XCarve.down();
		pause(10000);
		Addon.pumpOff();
		Addon.solenoidOn();
		pause(1500);
		Addon.solenoidOff();
		XCarve.up();
    }

    protected void pick() {
		XCarve.down();
		pause(10000);
		Addon.pumpOn();
		pause(1000);
		XCarve.up();
    }
    
    protected void pause(long millis) {
    	try {
    		Thread.sleep(millis);
    	}
    	catch(InterruptedException e) {}
    }

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		// TODO Auto-generated method stub
		Object o = e.getSource();
		if(o == bNorth) north();
		if(o == bSouth) south();
		if(o == bEast ) east();
		if(o == bWest ) west();
		if(o == bPick ) pick();
		if(o == bDrop ) drop();
		if(o == bLeft ) left();
		if(o == bRight) right();
	}
}
