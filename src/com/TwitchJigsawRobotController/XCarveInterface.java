package com.TwitchJigsawRobotController;

import java.util.TimerTask;

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
	static float MIN_X = 0;   // mm
	static float MAX_Y = 800; // mm
	static float MIN_Y = 0;   // mm
	static float MAX_Z = 0;  // mm
	static float MIN_Z = -80;   // mm
    
	private String serial_recv_buffer=new String();
	private SerialPort xCarvePort;
    private float x,y,z;
    private long lastReceivedTime;
    //private Timer disconnectTimer;
    

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

	
    protected void send(String msg) {
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
        send("$H");
	}


	public void startupInstructions() {
        goHome();
        setPosition(0,0,0);  // make sure it knows it is at 0,0,0
        send("G54");  // default coordinate system
        relativeMode();
        
        System.out.println("initialized X-Carve");
	}

	public void setPosition(float _x,float _y,float _z) {
		x=_x;
		y=_y;
		z=_z;
        send("G92 X"+x+" Y"+y+" Z"+z);
	}
	
	public void relativeMode() {
        send("G91");  // relative mode
	}

	public void absoluteMode() {
		send("G90");
	}
    
    public void north() {
    	goEast();
    }
    
    public void south() {
    	goWest();
    }
    public void east() {
    	goSouth();
    }
    
    public void west() {
    	goNorth();
    }

    protected void goNorth() {
    	if(y>=MAX_Y) return;
    	++y;
    	send("G0 Y+1");
    }
    
    protected void goSouth() {
    	if(y<=MIN_Y) return;
    	--y;
    	send("G0 Y-1");
    }
    protected void goEast() {
    	if(x>=MAX_X) return;
    	++x;
    	send("G0 X+1");
    }
    
    protected void goWest() {
    	if(x<=MIN_X) return;
    	--x;
    	send("G0 X-1");
    }

    public void up() {
    	if(z != MIN_Z) return;
    	z=MAX_Z;
    	absoluteMode();
    	send("G0 Z"+MAX_Z);
    	relativeMode();
    }
    
    public void down() {
    	if(z!=MAX_Z) return;
    	z=MIN_Z;
    	absoluteMode();
    	send("G0 Z"+MIN_Z);
    	relativeMode();
    }
    
    public String where() {
    	return new String("X"+x+" Y"+y+" Z"+z);
    }
}
