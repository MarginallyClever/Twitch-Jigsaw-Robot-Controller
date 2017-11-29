package com.TwitchJigsawRobotController;

import java.text.DecimalFormat;
import java.util.TimerTask;

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
    
	private String serial_recv_buffer=new String();
    private SerialPort addonPort;
    private int u,p,s;
    private long lastReceivedTime;
    //private Timer disconnectTimer;
    
	public AddonInterface() {
		u=0;
		p=0;
		s=0;
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
    
    public void pumpOn() {
    	p=1;
    	send("P0");
    }
    
    public void pumpOff() {
    	p=0;
    	send("P1");
    }
    
    public void pumpBlow() {
    	p=2;
    	send("P2");
    }
    
    public void turnLeft() {
    	u++;
    	send("R0");
    }
    
    public void turnRight() {
    	
    	u--;
    	send("R1");
    }
    
    public void solenoidOn() {
    	s++;
    	send("S1");
    }
    
    public void solenoidOff() {
    	s--;
    	send("S0");
    }
    
    public String where() {
    	float angle = (float)(u%200) * (360.0f/200.0f);
    	DecimalFormat df = new DecimalFormat();
    	df.setMaximumFractionDigits(2);
    	
    	return new String(""+df.format(angle)+"deg"/* P"+p+" S"+s*/);
    }
}
