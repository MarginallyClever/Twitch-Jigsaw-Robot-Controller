package com.TwitchJigsawRobotController;

import java.util.TimerTask;

import javax.swing.JOptionPane;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;

public class XCarveInterface implements SerialPortEventListener{
    static int BAUD_RATE = 115200;
	static String PORT_NAME_XCARVE = "COM4";
    static String CUE = "ok";
    static long DISCONNECT_CLOCK_DELAY = 20000;
    static long DISCONNECT_TIMEOUT = 60000;

    // software limits
	static float MAX_X = 800; // mm
	static float MIN_X = -30;   // mm
	static float MAX_Y = 800; // mm
	static float MIN_Y = 14;   // mm
	static float MAX_Z = 0;  // mm
	static float MIN_Z = -80;   // mm
    static float FEED_RATE = 500;  // mm
	private String serial_recv_buffer=new String();
	private SerialPort xCarvePort;
    private float x,y,z;
    private long lastReceivedTime;
    //private Timer disconnectTimer;
    
    public long getLastReceivedTime() {
    	return lastReceivedTime;
    }

    public XCarveInterface() {
		x=MIN_X;
		y=MIN_Y;
		z=MAX_Z;
    }
	
	protected void connect() throws Exception {
        System.out.println("Connecting to X-Carve...");
        xCarvePort = new SerialPort(XCarveInterface.PORT_NAME_XCARVE);
        try {
			xCarvePort.openPort();
			// Open serial port
	        xCarvePort.setParams(BAUD_RATE,SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_NONE);
	        xCarvePort.addEventListener(this);
		} catch (SerialPortException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			JOptionPane.showMessageDialog(null, "I cannot connect to the XCarve.  Is it on?  Connected?  E-stop off?  More information may be available in the Eclipse stack trace error message.","Error",JOptionPane.ERROR_MESSAGE);
			throw e;
		}
        try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        System.out.println("Connected to X-Carve");
        startupInstructions();
        lastReceivedTime = System.currentTimeMillis();
        setupDisconnectTimer();
	}
	
	
	protected void setupDisconnectTimer() {
		System.out.println("Starting timer...");
		//disconnectTimer = new Timer();
		//disconnectTimer.schedule(new DisconnectCheck(), DISCONNECT_CLOCK_DELAY);
	}
	
	
	public class DisconnectCheck extends TimerTask {
		public void run() {
			if(lastReceivedTime>DISCONNECT_TIMEOUT) {
				// one minute since last message from device
				System.out.println("XCarve disconnect suspected.  Reconnecting...");
				
				try {
					connect();
				}
				catch(SerialPortException e) {
					if(e.getExceptionType()==SerialPortException.TYPE_PORT_BUSY) {
						// not actually disconnected, still talking on the phone?
						lastReceivedTime=System.currentTimeMillis()+DISCONNECT_TIMEOUT;
					} else {
						JOptionPane.showMessageDialog(null, "I have lost connection to XCarve and cannot reconnect.  Is it on?  Connected?  E-stop off?","Error",JOptionPane.ERROR_MESSAGE);
						e.printStackTrace();
					}
				}
				catch(Exception e) {
					e.printStackTrace();
				}
			}
		}
	};
    
	
	// When a message is received from XCarve
	@Override
	public void serialEvent(SerialPortEvent arg0) {
        if(arg0.isRXCHAR()) {
            try {
                lastReceivedTime = System.currentTimeMillis();
                int len = arg0.getEventValue();
                byte[] buffer = xCarvePort.readBytes(len);
                String line2 = new String(buffer,0,len);

                serial_recv_buffer+=line2;
                int lastIndex = serial_recv_buffer.lastIndexOf('\n');
                if(lastIndex!=-1) {
	                String [] tokens = serial_recv_buffer.substring(0,lastIndex).split("\n");
	                for(int i=0;i<tokens.length;++i) {
	                	if(tokens[i].length()>0) {
	                		// every message from xcarve includes a \n
	                		System.out.print("X-Carve < "+tokens[i]);
	                		//sendMessage(CHANNEL, "[x-carve] said: "+tokens[i]);
	                		//trigger event for listeners
	                	}
	                }
	                serial_recv_buffer=serial_recv_buffer.substring(lastIndex+1);
                }
            } catch (SerialPortException e) {}
        }
	}

	
    protected void sendToXCarve(String msg) {
		try {
			if(!msg.endsWith("\n")) {
				msg+="\n";
			}
			System.out.print("X-Carve > "+msg);
			xCarvePort.writeBytes(msg.getBytes());
		}
		catch(SerialPortException e) {
			e.printStackTrace();
		}
    }
    
	
	public void goHome() {
        sendToXCarve("$H");
	}


	public void startupInstructions() {
        goHome();
        setPosition(MIN_X,MIN_Y,0);  // make sure it knows it is at 0,0,0
        sendToXCarve("G54");  // default coordinate system
        relativeMode();
        
        System.out.println("initialized X-Carve");
	}

	public void setPosition(float _x,float _y,float _z) {
		x=_x;
		y=_y;
		z=_z;
        sendToXCarve("G92 X"+x+" Y"+y+" Z"+z);
	}
	
	public void relativeMode() {
        sendToXCarve("G91");  // relative mode
	}

	public void absoluteMode() {
		sendToXCarve("G90");
	}
	
	public float getX() { return x; }
	public float getY() { return y; }

	/**
	 * is the coordinate within limits?
	 * @param newX in mm
	 * @param newY in mm
	 * @return true if in limits.
	 */
	public boolean isInBounds(float newX,float newY) {
		return (newX>=0 && newX< MAX_X 
			 && newY>=0 && newY< MAX_Y);
	}
	
	/**
	 * move the robot to a new x,y.
	 * @param newX
	 * @param newY
	 * @return false if out of bounds, true if move allowed.
	 */
    public boolean moveAbsolute(float newX,float newY) {
    	if(!isInBounds(newX,newY))
    		return false;

		x=newX;
		y=newY;
		absoluteMode();
		sendToXCarve("G0 X"+x+" Y"+y+" F"+FEED_RATE);
		relativeMode();
		
		return true;
    }
    
    /**
     * move X+1
     */
    public void north() {
    	goEast();
    }
    
    /**
     * move X-1
     */
    public void south() {
    	goWest();
    }

    /**
     * move Y-1
     */
    public void east() {
    	goSouth();
    }
    
    /**
     * move Y+1
     */
    public void west() {
    	goNorth();
    }

    
    protected void goNorth() {
    	if(y>=MAX_Y) return;
    	++y;
    	sendToXCarve("G0 Y+1");
    }
    
    protected void goSouth() {
    	if(y<=MIN_Y) return;
    	--y;
    	sendToXCarve("G0 Y-1");
    }
    protected void goEast() {
    	if(x>=MAX_X) return;
    	++x;
    	sendToXCarve("G0 X+1");
    }
    
    protected void goWest() {
    	if(x<=MIN_X) return;
    	--x;
    	sendToXCarve("G0 X-1");
    }

    public void up() {
    	if(z != MIN_Z) return;
    	z=MAX_Z;
    	absoluteMode();
    	sendToXCarve("G0 Z"+MAX_Z);
    	relativeMode();
    }
    
    public void down() {
    	if(z!=MAX_Z) return;
    	z=MIN_Z;
    	absoluteMode();
    	sendToXCarve("G0 Z"+MIN_Z);
    	relativeMode();
    }
    
    public String where() {
    	return new String("X"+x+" Y"+y+" Z"+z);
    }
}
