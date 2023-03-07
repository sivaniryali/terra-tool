#include "SevSeg.h"
#include <SoftwareSerial.h>
SoftwareSerial BlueTooth(0, 1);

int sensor_pin = A0;
int motor_pin = 3;
int output_value;
int oMoist = 0;
int nMoist = 0;
String text;
char eChar = '\r';
const char *textP;
int dryness;
int moisture;
bool motorPinState = false;

void setup() {
  Serial.begin(9600);
  Serial.println("Serial Reading From the Sensor ...");
  delay(2000);
  Serial.println("Setting 4 in output mode");
  pinMode(motor_pin, OUTPUT);
}

void loop() {
  if (Serial.available() != 0) {
    text = Serial.readStringUntil(eChar);
    nMoist = text.toInt();
    if (nMoist != oMoist) {
      Serial.print("Value from serial changed to ");
      Serial.println(nMoist);
      oMoist = nMoist;
    }
  }
  output_value = analogRead(sensor_pin);
  Serial.print("output value : ");
  Serial.println(output_value);
  dryness = map(output_value, 290, 1022, 0, 100);
  Serial.print("Dryness : ");
  Serial.println(dryness);  
  moisture = 100 - dryness;
  Serial.print("Moisture content : ");
  Serial.println(moisture);
  if (moisture < oMoist) {
    Serial.println("Setting motor_pin to HIGH");
    digitalWrite(motor_pin, HIGH);
    motorPinState = true;
  } else if ((moisture >= oMoist) && (moisture < (oMoist + 3))) {
    if (motorPinState == true) {
      Serial.println("Continuing Motor in High");
    } else {
      Serial.println("Continuing Motor in Low");
    }
  } else {
    Serial.println("Setting motor_pin to LOW");
    digitalWrite(motor_pin, LOW);
    motorPinState = false;
  }
  delay(3000);
}
