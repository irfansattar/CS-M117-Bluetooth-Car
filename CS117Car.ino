//www.elegoo.com
//2016.09.19

#include <Servo.h> //servo library

#define LT1 digitalRead(10)
#define LT2 digitalRead(4)
#define LT3 digitalRead(2)

//for line begin
#define ENA_line 5
#define ENB_line 11
#define IN1 6
#define IN2 7
#define IN3 8
#define IN4 9

#define ABS_line 150

//line end


#define MIDDLE_ANGLE 130
#define STOP_DIST 40

Servo myservo; // create servo object to control servo
int Echo = A4;  
int Trig = A5; 

int LED=13;
volatile int state = LOW;
char getstr;
int in1=6;
int in2=7;
int in3=8;
int in4=9;
int ENA=5;
int ENB=11;
int ABS=200;

// 0 - autonomous, 1 - manual, 2 - line following
int operation_mode;

//line begin
void back(){
  analogWrite(ENA_line, ABS_line);
  analogWrite(ENB_line, ABS_line);
  digitalWrite(IN1, HIGH);
  digitalWrite(IN2, LOW);
  digitalWrite(IN3, LOW);
  digitalWrite(IN4, HIGH);
  Serial.println("go back!");
}

void forward(){
  analogWrite(ENA_line, ABS_line);
  analogWrite(ENB_line, ABS_line);
  digitalWrite(IN1, LOW);
  digitalWrite(IN2, HIGH);
  digitalWrite(IN3, HIGH);
  digitalWrite(IN4, LOW);
  Serial.println("go forward!");
}

void left(){
  analogWrite(ENA_line, ABS_line);
  analogWrite(ENB_line, ABS_line);
  digitalWrite(IN1, HIGH);
  digitalWrite(IN2, LOW);
  digitalWrite(IN3, HIGH);
  digitalWrite(IN4, LOW); 
  Serial.println("go left!");
}

void right(){
  analogWrite(ENA_line, ABS_line);
  analogWrite(ENB_line, ABS_line);
  digitalWrite(IN1, LOW);
  digitalWrite(IN2, HIGH);
  digitalWrite(IN3, LOW);
  digitalWrite(IN4, HIGH);
  Serial.println("go right!");
} 

void stop(){
   digitalWrite(ENA_line, LOW);
   digitalWrite(ENB_line, LOW);
   Serial.println("Stop!");
} 


//line end
void _mBack()
{ 
  analogWrite(ENA,ABS);
  analogWrite(ENB,ABS);
  digitalWrite(in1,HIGH);//digital output
  digitalWrite(in2,LOW);
  digitalWrite(in3,LOW);
  digitalWrite(in4,HIGH);
  Serial.println("Forward");
}
void _mForward()
{
  analogWrite(ENA,ABS);
  analogWrite(ENB,ABS);
  digitalWrite(in1,LOW);
  digitalWrite(in2,HIGH);
  digitalWrite(in3,HIGH);
  digitalWrite(in4,LOW);
  Serial.println("Back");
}
void _mleft()
{
  analogWrite(ENA,ABS);
  analogWrite(ENB,ABS);
  digitalWrite(in1,HIGH);
  digitalWrite(in2,LOW);
  digitalWrite(in3,HIGH);
  digitalWrite(in4,LOW); 
  Serial.println("go left!");
}
void _mright()
{
  analogWrite(ENA,ABS);
  analogWrite(ENB,ABS);
  digitalWrite(in1,LOW);
  digitalWrite(in2,HIGH);
  digitalWrite(in3,LOW);
  digitalWrite(in4,HIGH);
  Serial.println("go right!");
}
void _mStop()
{
  digitalWrite(ENA,LOW);
  digitalWrite(ENB,LOW);
  Serial.println("Stop!");
}

 /*Ultrasonic distance measurement Sub function*/
int Distance_test()   
{
  digitalWrite(Trig, LOW);   
  delayMicroseconds(2);
  digitalWrite(Trig, HIGH);  
  delayMicroseconds(20);
  digitalWrite(Trig, LOW);   
  float Fdistance = pulseIn(Echo, HIGH);  
  Fdistance= Fdistance/58;       
  return (int)Fdistance;
} 

void stateChange()
{
  state = !state;
  digitalWrite(LED, state);  
}

void setup()
{ 
  pinMode(LED, OUTPUT);
  myservo.attach(3);// attach servo on pin 3 to servo object
  Serial.begin(9600);     
  pinMode(Echo, INPUT);    
  pinMode(Trig, OUTPUT);
  pinMode(in1,OUTPUT);
  pinMode(in2,OUTPUT);
  pinMode(in3,OUTPUT);
  pinMode(in4,OUTPUT);
  pinMode(ENA,OUTPUT);
  pinMode(ENB,OUTPUT);
  _mStop();

  operation_mode = 1;
  myservo.write(MIDDLE_ANGLE);
}

char in; 
void loop()
{
  // Check bluetooth input values
  if (Serial.available())
  {
    in = Serial.read();
    if (in == '0' || in == '1' || in == '2')
    {
      _mStop();
      operation_mode = in - '0';
      Serial.println(operation_mode);

      if (operation_mode == 1)
      {
        myservo.write(MIDDLE_ANGLE);
        delay(500);
      }
      else if (operation_mode == 0)
      {
        myservo.write(MIDDLE_ANGLE);
        delay(500); 
      }
    }
  }

  int middleDistance, rightDistance, leftDistance;
  switch (operation_mode)
  {
    // AUTONOMOUS MODE   
    case 0:
      middleDistance = Distance_test();
      #ifdef send
      Serial.print("middleDistance=");
      Serial.println(middleDistance);
      #endif
  
      if(middleDistance<= STOP_DIST)
      {     
        Serial.println("c");
        _mStop();
        delay(500);     
        myservo.write(MIDDLE_ANGLE - 90);//10°-180°          
        delay(1000);      
        rightDistance = Distance_test();
  
        #ifdef send
        Serial.print("rightDistance=");
        Serial.println(rightDistance);
        #endif
  
        delay(500);
        // myservo.write(135);              
        //delay(1000);                                                  
        myservo.write(MIDDLE_ANGLE + 90);              
        delay(1000); 
        leftDistance = Distance_test();
  
        #ifdef send
        Serial.print("leftDistance=");
        Serial.println(leftDistance);
        #endif
  
        delay(500);
        myservo.write(MIDDLE_ANGLE);              
        delay(1000);
        if(rightDistance>leftDistance)  
        {
          _mright();
          delay(360);
         }
         else if(rightDistance<leftDistance)
         {
          _mleft();
          delay(360);
         }
         else if((rightDistance <= STOP_DIST) || (leftDistance<= STOP_DIST))
         {
          _mBack();
          delay(180);
         }
         else
         {
          _mForward();
         }
      }  
      else
          _mForward();
      
      break;

    // MANUAL MODE
    case 1:
      // check for collision
      middleDistance = Distance_test();
      #ifdef send
      Serial.print("middleDistance=");
      Serial.println(middleDistance);
      #endif

      if (middleDistance <= STOP_DIST)
      {
        Serial.println("c");
        _mStop();
        delay(100);
        _mright();
        delay(850);
        _mStop();
      }
      
      // read input from bluetooth
     if(in =='f')
        _mForward();
      else if(in =='b')
      {
        _mBack();
        //delay(20);
      }
      else if(in =='l')
      {
        _mleft();
        //delay(20);
      }
      else if(in =='r')
      {
        _mright();
        //delay(20);
      }
      else if(in =='s')
      {
         _mStop();     
      }
      else if(in =='A')
      {
        stateChange();
      }


      break;


    // LINE FOLLOWING MODE
    case 2:
      // check for collision

      
      // read IR sensors
      

      // move based on sensor values
       if (LT2 == HIGH) {
              forward();
       } 
       //else if(LT1 == HIGH) {
//          forward();
//        } 
        else if (LT3 == HIGH) {
         forward();
         //while (LT3 == HIGH);
         //stop();
      } else {
        right();
      }

       break;   
       default:

      break;
    }
}
