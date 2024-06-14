#include <ESP8266WiFi.h>
#include <Firebase_ESP_Client.h>
#include "addons/TokenHelper.h"
#include "addons/RTDBHelper.h"
#include <Wire.h>
#include <DFRobot_MAX30102.h>

#define WIFI_SSID "" // user's wifi name
#define WIFI_PASSWORD "" // user's wifi password

#define API_KEY "" // firebase api key
#define DATABASE_URL "" // firebase url

#define USER_EMAIL "" // user's account email
#define USER_PASSWORD "" // user's account password

FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;
DFRobot_MAX30102 particleSensor;

const unsigned long timeUpdateInterval = 5000;
unsigned long lastTimeUpdateMillis = 0;

unsigned long sendDataPrevMillis = 0;
bool signupOK = false;

int32_t heartRate;
int8_t heartRateValid;
int32_t SPO2;
int8_t SPO2Valid;


void setup() {
  Serial.begin(115200);
  while (!particleSensor.begin()) {
    Serial.println("MAX30102 was not found");
  }
  Serial.println("Place your index finger on the sensor with steady pressure.");

  particleSensor.sensorConfiguration(/*ledBrightness=*/50, /*sampleAverage=*/SAMPLEAVG_4, \
                        /*ledMode=*/MODE_MULTILED, /*sampleRate=*/SAMPLERATE_100, \
                        /*pulseWidth=*/PULSEWIDTH_411, /*adcRange=*/ADCRANGE_16384);

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  Serial.print("Connecting to Wi-Fi");
  while (WiFi.status() != WL_CONNECTED) {
    Serial.print(".");
    delay(300);
  }
  Serial.println();
  Serial.print("Connected with IP: ");
  Serial.println(WiFi.localIP());
  Serial.println();

  config.api_key = API_KEY;
  config.database_url = DATABASE_URL;

  // Sign in with email and password
  auth.user.email = EMAIL;
  auth.user.password = PASSWORD;
  if (EMAIL != "" && PASSWORD != "") {
    signupOK = true;
    Serial.println("Successfully Logged in");
  }
  else{
    Serial.println("Something went Wrong");
  }


  config.token_status_callback = tokenStatusCallback;
  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);
  fbdo.setBSSLBufferSize(4096, 1024);
  config.timeout.networkReconnect = 10 * 1000;
  config.timeout.serverResponse = 10 * 1000;
  config.timeout.rtdbKeepAlive = 45 * 1000;
  config.timeout.rtdbStreamReconnect = 1 * 1000;
}

void loop() {
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println(F("Wait about four seconds"));
    particleSensor.heartrateAndOxygenSaturation(&SPO2, &SPO2Valid, &heartRate, &heartRateValid);
    
    Serial.print(F("heartRate="));
    Serial.print(heartRate, DEC);
    Serial.print(F(", heartRateValid="));
    Serial.print(heartRateValid, DEC);

    if (Firebase.ready() && signupOK && (millis() - sendDataPrevMillis > 30000 || sendDataPrevMillis == 0)) {
      sendDataPrevMillis = millis();
      int bpmFB = heartRate;
      if (Firebase.RTDB.pushInt(&fbdo, "Users/space provided/MAX30102/BPM", bpmFB)) { // user's unique id inside of the space provided
        Serial.println();
        Serial.print(heartRate);
        Serial.print(" - successfully saved to: " + fbdo.dataPath());
        Serial.println("(" + fbdo.dataType() + ")");
      } else {
        Serial.println("FAILED: " + fbdo.errorReason());
      }
    }
  }
}