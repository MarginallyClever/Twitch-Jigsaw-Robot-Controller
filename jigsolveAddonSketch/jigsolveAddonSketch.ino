//----------------------------------------------------------------------------
// Jigsolve firmware
// drives rotation and suction motors on XCarve jigsaw robot
// dan@marginallyclever.com 2017-01-21
//----------------------------------------------------------------------------


#include <AFMotor.h>


//----------------------------------------------------------------------------

#define VERBOSE 0  // increase number for more output.  0 for minimum.

#define BAUD 115200
#define BUF_MAX 64

//#define SOLENOID_PIN 9
#define SOLENOID_PIN 10


//----------------------------------------------------------------------------


AF_DCMotor motor(4);
//AF_DCMotor solenoid(3);

// Connect a stepper motor with 200 steps per revolution (1.8 degree)
// to motor port #1 (M1 and M2)
AF_Stepper stepper(200, 1);

char msg[BUF_MAX];
int bPos;
long lastPing;


//----------------------------------------------------------------------------


void setup() {
  Serial.begin(BAUD);
  
  Serial.println("dc motor prep...");
  motor.setSpeed(255);  // max
  motor.run(RELEASE);
  
  Serial.println("stepper prep...");
  stepper.setSpeed(60);  // rpm
  
  //solenoid.setSpeed(255);
  //solenoid.run(RELEASE);
  pinMode(SOLENOID_PIN,OUTPUT);
  
  bPos=0;

  Serial.println("Addon ready");
}

void loop() {
  if(Serial.available()>0) {
#if VERBOSE > 1
    Serial.print("Available ");
#endif
    int c = Serial.read();
    if(bPos<BUF_MAX-1) {
#if VERBOSE > 1
      Serial.print("Received ");
      Serial.println((char)c);
#endif
      // room to store more message
      msg[bPos++]=c;
    }
    
    if(c=='\n') {
      // message finished
      msg[bPos]=0;
#if VERBOSE > 0
      Serial.print(msg);
#endif
      if(msg[0]=='P') {
             if(msg[1]=='0') motor.run(BACKWARD);  // P0 = pump reverse
        else if(msg[1]=='1') motor.run(RELEASE );  // P1 = pump off
        else if(msg[1]=='2') motor.run(FORWARD );  // P2 = pump on
      } else if(msg[0]=='S') {
             if(msg[1]=='0') digitalWrite(SOLENOID_PIN,LOW );  //solenoid.run(BACKWARD);  // S0 = solenoid reverse
        else if(msg[1]=='1') digitalWrite(SOLENOID_PIN,HIGH);  //solenoid.run(RELEASE );  // S1 = solenoid off
        //else if(msg[1]=='2') digitalWrite(SOLENOID_PIN,HIGH);  //solenoid.run(FORWARD );  // S2 = solenoid on
      } else if(msg[0]=='R') {
             if(msg[1]=='0') stepper.step(1, FORWARD , SINGLE);  // R0 = turn ccw
        else if(msg[1]=='1') stepper.step(1, BACKWARD, SINGLE);  // R1 = turn cw
      }
      // restart
      bPos=0;
      Serial.println("ok");
    }
  }
  delay(50);
}

