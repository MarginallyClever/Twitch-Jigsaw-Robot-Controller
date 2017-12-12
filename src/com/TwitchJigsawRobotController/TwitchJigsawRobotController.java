package com.TwitchJigsawRobotController;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.BevelBorder;

import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacpp.opencv_videoio.VideoCapture;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.jibble.pircbot.*;
import org.junit.Test;

public class TwitchJigsawRobotController 
extends PircBot
implements ActionListener, PropertyChangeListener  {
	static public String nameRoot = "jigsolve";
    static public String CHANNEL = "#jigsolve";
    static public String NETWORK = "irc.chat.twitch.tv";
    static public int PORT = 6667;
    static public String PASSWORD = "oauth:j1xd8duzvlhuov9yczcrxgmo1eym3m";
	static public long ANNOUNCE_DELAY = 60000; // ms
	static public long SANITY_DROP_DELAY = 120000; // ms
	static public String MOVE_COMMAND = "GO";
	
	private XCarveInterface XCarve;
    private AddonInterface Addon;
    private String IPAddress;
    private Timer announcementTimer;
    
    protected JFrame frame;
    protected JPanel buttonPanel, statusPanel;
    protected JLabel statusLabel;
	protected JButton bNorth,bSouth,bEast,bWest,bPick,bDrop,bLeft,bRight,bCapture;
    
	private long lastMove;
    private boolean announceOnce;
    private boolean isPickedUp;
    
    protected float [] goCommandValues = new float[3];

    
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
		announceOnce=false;
		isPickedUp=false;
	}
	
	
	protected void setupDialog() {
		frame = new JFrame("Jigsaw Controller");
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.setSize(300, 350);
		
		buttonPanel = new JPanel();
		buttonPanel.setLayout(new GridLayout(3,3));
		buttonPanel.setMinimumSize(new Dimension(300,300));
		buttonPanel.setSize(new Dimension(300,300));

		bNorth = new JButton("North");
		bSouth = new JButton("South");
		bWest  = new JButton("West");
		bEast  = new JButton("East");
		bPick  = new JButton("Pick");
		bDrop  = new JButton("Drop");
		bLeft  = new JButton("CCW");
		bRight = new JButton("CW");
		bCapture  = new JButton("Capture");

		buttonPanel.add(bLeft);
		buttonPanel.add(bNorth);
		buttonPanel.add(bRight);
		buttonPanel.add(bWest);
		buttonPanel.add(bCapture);
		buttonPanel.add(bEast);
		buttonPanel.add(bPick);
		buttonPanel.add(bSouth);
		buttonPanel.add(bDrop);
		
		bCapture.addActionListener(this);
		bNorth.addActionListener(this);
		bSouth.addActionListener(this);
		bWest.addActionListener(this);
		bEast.addActionListener(this);
		bPick.addActionListener(this);
		bDrop.addActionListener(this);
		bLeft.addActionListener(this);
		bRight.addActionListener(this);

		// create the status bar panel and shove it down the bottom of the frame
		statusPanel = new JPanel();
		statusPanel.setBorder(new BevelBorder(BevelBorder.LOWERED));
		statusPanel.setPreferredSize(new Dimension(frame.getWidth(), 30));
		statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.X_AXIS));
		statusLabel = new JLabel("");
		statusLabel.setHorizontalAlignment(SwingConstants.LEFT);
		statusPanel.add(statusLabel);

		frame.setLayout(new BorderLayout());
		frame.add(buttonPanel);
		frame.add(statusPanel,BorderLayout.SOUTH);
		
		//frame.pack();
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
			if(announceOnce==false && System.currentTimeMillis()>lastMove+ANNOUNCE_DELAY) {
				// announce the location of the robot
				where(CHANNEL);
				announceOnce=true;
			} 
			if(isPickedUp==true && System.currentTimeMillis()>lastMove+SANITY_DROP_DELAY) {
				System.out.println("Automated sanity drop");
				drop();
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
				JOptionPane.showMessageDialog(null, "I failed to connect to Twitch IRC.  It's an IRC problem.","Error",JOptionPane.ERROR_MESSAGE);
	        	e.printStackTrace();
	        	throw e;
	        }
	        catch(IOException e) {
				JOptionPane.showMessageDialog(null, "I failed to connect to Twitch IRC.  It's an IO problem.","Error",JOptionPane.ERROR_MESSAGE);
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
    	else ignore=parseGo(message);
    	// else ignore
    	     
    	if(ignore==false) {
    		lastMove=System.currentTimeMillis();
    		announceOnce=false;
    	}
    }
    
	/**
	 * Parse messages in the format "GO [Xnnn] [Ynnn]".  nnn is a float.  X and Y parts are optional.
	 * @param message the string sent from the 
	 * @return true if message was ignored.
	 */
	protected boolean parseGo(String message) {
		// check if input is sane and parse values
		if(!message.startsWith(MOVE_COMMAND))
			return true;  // does not start with right command.

		float oldX = goCommandValues[0] = XCarve.getX();
		float oldY = goCommandValues[1] = XCarve.getY();
		float oldA = goCommandValues[2] = Addon.getAngleDegrees();

		boolean looksSane = analyzeMessage(message);
		if(!looksSane) return true;
		
		// input looks sane
		float newX = goCommandValues[0];
		float newY = goCommandValues[1];
		float newA = goCommandValues[2];
		
		if(newX!=oldX || newY!=oldY) {
			// new command actually moves to a new place
			if(!XCarve.isInBounds(newX, newY)) {
				sendMessage(CHANNEL,"X"+newX +" Y"+newY+" is out of bounds.");
			} else {
				sendMessage(CHANNEL,"Moving to X"+newX +" Y"+newY+"...");
				XCarve.moveAbsolute(newX, newY);
			}
		}
		if(Addon.convertDegreesToSteps(newA) != Addon.convertDegreesToSteps(oldA)) {
			// new command actually moves to a new place
			if(newA<0 || newA>360) {
				sendMessage(CHANNEL,"A"+newA+" is out of bounds.");
			} else {
				sendMessage(CHANNEL,"Turning from A"+oldA+" to A"+newA);
				Addon.turnAbsolute(newA);
			}
		}
    	getLocation();
		
		return false;
	}
    
    public void captureFrame() {
    	System.out.println("captureFrame() begin");
    	VideoCapture cap = new VideoCapture();
    	cap.open("http://192.168.0.100:12345/?action=stream");
    	if(cap.isOpened()) {
        	System.out.println("stream open");
    		Mat mat = new Mat();
    		if(cap.read(mat)) {
    			System.out.println("frame read");

    			Java2DFrameConverter frameToImage = new Java2DFrameConverter();
    			OpenCVFrameConverter.ToMat matToFrame = new OpenCVFrameConverter.ToMat();
    			
    			Frame frame = matToFrame.convert(mat);
    			BufferedImage image = frameToImage.convert(frame);
    			try {
    				File outputfile = new File("saved.jpg");
    			    ImageIO.write(image, "jpg", outputfile);	
    				System.out.println("Frame written");
    			} catch(Exception e) {
    				e.printStackTrace();
    			}
    		}
    		cap.close();
    	}
    	System.out.println("captureFrame() end");
    }
	
	/**
	 * 
	 * @param message
	 * @return true if message is sane
	 */
	protected boolean analyzeMessage(String message) {
		StringTokenizer st = new StringTokenizer(message);
		while(st.hasMoreTokens()) {
			String tok=st.nextToken();
			
			try {
					 if(tok.equals(MOVE_COMMAND)) ;	// first item on string?  I don't care if someone writes "go go go x100 go"
				else if(tok.startsWith("X")) goCommandValues[0] = getValueFromToken(tok);
				else if(tok.startsWith("Y")) goCommandValues[1] = getValueFromToken(tok);
				else if(tok.startsWith("A")) goCommandValues[2] = getValueFromToken(tok);
				else  {
					// badly formed commands are ignored, even if some parts are OK.
					sendMessage(CHANNEL,"I don't know what '"+tok+"' means.");
					return false;
				}
			} catch(NumberFormatException e) {
				return false;
			}
		}
		return true;
	}
	
	protected float newRandomFloat(float min,float max) {
		return min + (float)Math.random() * (max-min);
	}
	
	protected boolean fuzzyEquals(double a, double b) {
	    return Math.abs(a - b) < 0.000001 * Math.max(Math.abs(a), Math.abs(b));
	}
	
	@Test
	public void testAnalyzeMessage() {
		System.out.println("testAnalyzeMessage() Begin");
		System.out.println("test full message");
		for(int i=0;i<1000;++i) {
			float x = newRandomFloat(XCarveInterface.MIN_X,XCarveInterface.MAX_X);
			float y = newRandomFloat(XCarveInterface.MIN_Y,XCarveInterface.MAX_Y);
			float a = newRandomFloat(AddonInterface.MIN_A,AddonInterface.MAX_A);
			String message = MOVE_COMMAND+" X"+x+" Y"+y+" A"+a;
			System.out.println(message);
			
			goCommandValues[0]=x;
			goCommandValues[1]=y;
			goCommandValues[2]=a;
			assert(analyzeMessage(message));
			System.out.println(goCommandValues[0] + " "+goCommandValues[1]+" "+goCommandValues[2]);
			assert(fuzzyEquals(goCommandValues[0],x));
			assert(fuzzyEquals(goCommandValues[1],y));
			assert(fuzzyEquals(goCommandValues[2],a));
		}

		System.out.println("test only x");
		for(int i=0;i<1000;++i) {
			float x = newRandomFloat(XCarveInterface.MIN_X,XCarveInterface.MAX_X);
			String message = MOVE_COMMAND+" X"+x;
			System.out.println(message);

			goCommandValues[0]=x;
			goCommandValues[1]=0;
			goCommandValues[2]=0;
			assert(analyzeMessage(message));
			assert(fuzzyEquals(goCommandValues[0],x));
			assert(fuzzyEquals(goCommandValues[1],0));
			assert(fuzzyEquals(goCommandValues[2],0));
		}

		System.out.println("test only y");
		for(int i=0;i<1000;++i) {
			float y = newRandomFloat(XCarveInterface.MIN_Y,XCarveInterface.MAX_Y);
			String message = MOVE_COMMAND+" Y"+y;
			System.out.println(message);

			goCommandValues[0]=0;
			goCommandValues[1]=y;
			goCommandValues[2]=0;
			assert(analyzeMessage(message));
			assert(fuzzyEquals(goCommandValues[0],0));
			assert(fuzzyEquals(goCommandValues[1],y));
			assert(fuzzyEquals(goCommandValues[2],0));
		}
		
		System.out.println("test only a");
		for(int i=0;i<1000;++i) {
			float a = newRandomFloat(AddonInterface.MIN_A,AddonInterface.MAX_A);
			String message = MOVE_COMMAND+" A"+a;
			System.out.println(message);

			goCommandValues[0]=0;
			goCommandValues[1]=0;
			goCommandValues[2]=a;
			assert(analyzeMessage(message));
			assert(fuzzyEquals(goCommandValues[0],0));
			assert(fuzzyEquals(goCommandValues[1],0));
			assert(fuzzyEquals(goCommandValues[2],a));
		}
		
		System.out.println("testAnalyzeMessage() OK");
	}
	
	protected char getLetterFromToken(String token) {
		return token.charAt(0);
	}
	
	protected float getValueFromToken(String token) throws NumberFormatException {
		float x;
		try {
			x = Float.parseFloat(token.substring(1));
		} catch(NumberFormatException e) {
			System.out.println("Bad format for: "+token);
			throw e;
		}
		return x;		
	}
	
	@Test
	public void testValueFromToken() {
		System.out.println("testValueFromToken()");
		try {
			assert(getValueFromToken("X100") == 100);
			assert(getValueFromToken("Y-20") == -20);
			assert(getValueFromToken("Z1.0E25") == 1.0E25);
			assert(getValueFromToken("W361.0") == 361);
		} catch( NumberFormatException e) {
			assert(false);
		}
		try {
			// will throw assert
			getValueFromToken("W361.0andsomethingbreaks");
			assert(false);
		} catch( NumberFormatException e) {
			assert(true);
		}

		try {
			// will throw assert
			getValueFromToken("abcd-0andsomethingbreaks");
			assert(false);
		} catch( NumberFormatException e) {
			assert(true);
		}
		System.out.println("testValueFromToken() complete.");
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

    protected String getLocation() {
    	String str = XCarve.where() + " " + Addon.where();
    	if(statusLabel!=null) statusLabel.setText(str);    	
    	return str;
    }
    
    protected void where(String sender) {
		// report machine state here
		String arg1 = "I am at "+/*IPAddress+":12345 "+*/ getLocation();
		if(sender.equals(CHANNEL)) sendMessage(CHANNEL,arg1);
		else privMessage(sender, arg1);
    }
    
    protected void north() {
    	sendMessage(CHANNEL,"North");
    	getLocation();
    	XCarve.north();
    }
    protected void south() {
    	sendMessage(CHANNEL,"South");
    	getLocation();
    	XCarve.south();
    }
    protected void east() {
    	sendMessage(CHANNEL,"East");
    	getLocation();
    	XCarve.east();
    }
    protected void west() {
    	sendMessage(CHANNEL,"West");
    	getLocation();
    	XCarve.west();
    }
    protected void left() {
    	sendMessage(CHANNEL,"Left");
    	getLocation();
    	Addon.turnRight();
    }
    protected void right() {
    	sendMessage(CHANNEL,"Right");
    	getLocation();
    	Addon.turnLeft();
    }
    protected void drop() {
    	sendMessage(CHANNEL,"Dropping...");
		XCarve.down();
		pause(10000);
		Addon.pumpOff();
		Addon.solenoidOn();
		pause(1500);
		Addon.solenoidOff();
		XCarve.up();
		isPickedUp=false;
    }

    protected void pick() {
    	sendMessage(CHANNEL,"Picking...");
		XCarve.down();
		pause(10000);
		Addon.pumpOn();
		pause(1000);
		XCarve.up();
		isPickedUp=true;
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
		if(o == bCapture) captureFrame();
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
