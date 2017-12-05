package com.TwitchJigsawRobotController;

import java.text.DecimalFormat;
import java.util.TimerTask;

import javax.swing.JOptionPane;

import org.junit.Test;

import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;

public class AddonInterface implements SerialPortEventListener {
    static int BAUD_RATE = 115200;
    static String PORT_NAME_ADDON = "COM71";
    static String CUE = "ok";
    static long DISCONNECT_CLOCK_DELAY = 20000;
    static long DISCONNECT_TIMEOUT = 60000;
    static int STEPS_PER_TURN = 200;
    static int HALF_STEPS_PER_TURN = STEPS_PER_TURN /2; 
    static float MIN_A = 0;
    static float MAX_A = 360;
    
	private String serial_recv_buffer=new String();
    private SerialPort addonPort;
    private int angle,pumpOn,solenoidOn;
    private long lastReceivedTime;
    //private Timer disconnectTimer;
    
	public AddonInterface() {
		angle=0;
		pumpOn=0;
		solenoidOn=0;
	}
	
	protected void connect() throws Exception {
        System.out.println("Connecting to Addon...");
        addonPort = new SerialPort(PORT_NAME_ADDON);
        try {
        	addonPort.openPort();
			// Open serial port
			addonPort.setParams(BAUD_RATE,SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_NONE);
	        addonPort.addEventListener(this);
		} catch (SerialPortException e) {
			// TODO Auto-generated catch block
			//e.printStackTrace();
			JOptionPane.showMessageDialog(null, "I cannot connect to the Addon.  Is it on?  is it connected?  More information may be available in the Eclipse stack trace error message.","Error",JOptionPane.ERROR_MESSAGE);

			throw e;
		}
        System.out.println("Connected to Addon");
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
				System.out.println("Addon disconnect suspected.  Reconnecting...");
				// one minute since last message from device
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
    
	
	// When a message is received
	@Override
	public void serialEvent(SerialPortEvent arg0) {
        if(arg0.isRXCHAR()) {
            try {
                lastReceivedTime = System.currentTimeMillis();
                
                int len = arg0.getEventValue();
                byte[] buffer = addonPort.readBytes(len);
                String line2 = new String(buffer,0,len);

                serial_recv_buffer+=line2;
                int lastIndex = serial_recv_buffer.lastIndexOf('\n');
                if(lastIndex!=-1) {
	                String [] tokens = serial_recv_buffer.substring(0,lastIndex).split("\n");
	                for(int i=0;i<tokens.length;++i) {
	                	if(tokens[i].length()>0) {
	                		System.out.print("Addon < "+tokens[i]);
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
			System.out.print("Addon > ");
			addonPort.writeBytes(msg.getBytes());
			System.out.print(msg);
		}
		catch(SerialPortException e) {
			e.printStackTrace();
		}
    }
    
    public boolean isPumpOn() {
    	return pumpOn!=0;
    }
    
    public boolean isSolenoidOn() {
    	return solenoidOn==1;
    }
    
    public void pumpOn() {
    	pumpOn=1;
    	send("P0");
    }
    
    public void pumpOff() {
    	pumpOn=0;
    	send("P1");
    }
    
    public void pumpBlow() {
    	pumpOn=2;
    	send("P2");
    }
    
    public void turnLeft() {
    	angle= (angle + STEPS_PER_TURN +1 ) % STEPS_PER_TURN;
    	send("R0");
    }
    
    public void turnRight() {
    	angle= (angle + STEPS_PER_TURN -1 ) % STEPS_PER_TURN;
    	send("R1");
    }
    
    public void solenoidOn() {
    	solenoidOn=1;
    	send("S1");
    }
    
    public void solenoidOff() {
    	solenoidOn=0;
    	send("S0");
    }
    
    public float getAngleDegrees() {
    	// convert motor steps to degrees 
    	float angleDegrees = (float)(angle%STEPS_PER_TURN) * (360.0f/(float)STEPS_PER_TURN);
    	return angleDegrees;
    }
    
    static public int calculateShortestPath(int oldAngle,int newAngle) {
    	int delta = newAngle - oldAngle;
    	if(delta>0 &&          delta  > HALF_STEPS_PER_TURN) delta = STEPS_PER_TURN - delta;
    	if(delta<0 && Math.abs(delta) > HALF_STEPS_PER_TURN) delta = STEPS_PER_TURN + delta;
    	return delta;
    }
    
    public void turnAbsolute(float newAngleDegrees) {
    	// convert degrees to motor steps
    	int newAngle = (int)( newAngleDegrees * (float)STEPS_PER_TURN / 360.0f );
    	newAngle %= STEPS_PER_TURN;
    	
    	// from here on all values are in motor steps.
    	int delta = calculateShortestPath(angle,newAngle);    	
    	if(delta<0) {
    		delta=-delta;
    		for(int i=0;i<delta;++i) {
    			turnLeft();
    		}
    	} else {
    		// delta > 0
    		for(int i=0;i<delta;++i) {
    			turnRight();
    		}
    	}
    }
    
    public String where() {
    	float angleDegrees = getAngleDegrees();
    	DecimalFormat df = new DecimalFormat();
    	df.setMaximumFractionDigits(2);
    	
    	return new String("A"+df.format(angleDegrees)/*+" P"+p+" S"+s*/);
    }
    
    @Test
    public void testShortestPath() {
    	System.out.println("test when start<end and abs(end-start)<180");
    	for(int start=0;start<STEPS_PER_TURN;++start) {
        	for(int end=start;end<start+HALF_STEPS_PER_TURN;++end) {
        		int result=calculateShortestPath(start,end);
        		System.out.println("\t"+start+"\t"+end+"\t"+(end-start)+"\t"+result);
        		assert(result>=0 && result<=HALF_STEPS_PER_TURN);
        	}
    	}
    	System.out.println("test when start<end and abs(end-start)>=180");
    	for(int start=0;start<STEPS_PER_TURN;++start) {
        	for(int end=start+HALF_STEPS_PER_TURN;end<start+STEPS_PER_TURN;++end) {
        		int result=calculateShortestPath(start,end);
        		System.out.println("\t"+start+"\t"+end+"\t"+(end-start)+"\t"+result);
        		assert(result<=0 && result>=-HALF_STEPS_PER_TURN);
        	}
    	}
    	System.out.println("test when end<start and abs(end-start)<180");
    	for(int start=0;start<STEPS_PER_TURN;++start) {
        	for(int end=start;end>start-HALF_STEPS_PER_TURN;--end) {
        		int result=calculateShortestPath(start,end);
        		System.out.println("\t"+start+"\t"+end+"\t"+(end-start)+"\t"+result);
        		assert(result<=0 && result>=-HALF_STEPS_PER_TURN);
        	}
    	}
    	System.out.println("test when end<start and abs(end-start)>=180");
    	for(int start=0;start<STEPS_PER_TURN;++start) {
        	for(int end=start-HALF_STEPS_PER_TURN;end>start-STEPS_PER_TURN;--end) {
        		int result=calculateShortestPath(start,end);
        		System.out.println("\t"+start+"\t"+end+"\t"+(end-start)+"\t"+result);
        		assert(result>=0 && result<=HALF_STEPS_PER_TURN);
        	}
    	}
    	System.out.println("Test OK");
    }
}
