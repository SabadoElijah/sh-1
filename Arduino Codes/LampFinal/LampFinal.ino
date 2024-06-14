#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include <addons/TokenHelper.h>
#include <addons/RTDBHelper.h>
#include <Adafruit_NeoPixel.h>

#define WIFI_SSID "" // user's wifi name
#define WIFI_PASSWORD "" // user's wifi password

#define API_KEY "" // firebase api key
#define DATABASE_URL "" // firebase url

#define USER_EMAIL "" // user's account email
#define USER_PASSWORD "" // user's account password

#define PIN        12
#define NUMPIXELS  64
#define MIC        16
#define relay      5
#define lightPath "Users/ /DeviceStatus/Light" // user's unique ID inside of the space provided
#define humidifierPath "Users/ /DeviceStatus/Humidifier" // user's unique ID inside of the space provided
#define soundSensorPath "Users/ /DeviceData/SoundSensor" // user's unique ID inside of the space provided

Adafruit_NeoPixel pixels(NUMPIXELS, PIN, NEO_GRB + NEO_KHZ800);

// Define Firebase Data object
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

unsigned long sendDataPrevMillis = 0;
unsigned long count = 0;
int soundSensorVal = 0;
int soundSensorRelay = 0;

void setup()
{
    Serial.begin(115200);
    pinMode(MIC, INPUT);  // define arduino pin
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    Serial.print("Connecting to Wi-Fi");
    while (WiFi.status() != WL_CONNECTED)
    {
        Serial.print(".");
        delay(300);
    }
    Serial.println();
    Serial.print("Connected with IP: ");
    Serial.println(WiFi.localIP());
    Serial.println();

    Serial.printf("Firebase Client v%s\n\n", FIREBASE_CLIENT_VERSION);

    /* Assign the api key (required) */
    config.api_key = API_KEY;

    /* Assign the user sign in credentials */
    auth.user.email = USER_EMAIL;
    auth.user.password = USER_PASSWORD;

    /* Assign the RTDB URL (required) */
    config.database_url = DATABASE_URL;

    /* Assign the callback function for the long running token generation task */
    config.token_status_callback = tokenStatusCallback; // see addons/TokenHelper.h

    // Comment or pass false value when WiFi reconnection will control by your code or third party library e.g. WiFiManager
    Firebase.reconnectNetwork(true);

    fbdo.setBSSLBufferSize(4096, 1024);

    // To connect without auth in Test Mode, see Authentications/TestMode/TestMode.ino
    Firebase.begin(&config, &auth);

    // You can use TCP KeepAlive in FirebaseData object and tracking the server connection status, please read this for detail.
    // https://github.com/mobizt/Firebase-ESP32#about-firebasedata-object
    // fbdo.keepAlive(5, 5, 1);

    config.timeout.networkReconnect = 10 * 1000;
    config.timeout.serverResponse = 10 * 1000;
    config.timeout.rtdbKeepAlive = 45 * 1000;
    config.timeout.rtdbStreamReconnect = 1 * 1000;
    /** Timeout options.
     * You can also set the TCP data sending retry with
     * config.tcp_data_sending_retry = 1;
     */
    pinMode(relay, OUTPUT);
}

void loop()
{
    if (Firebase.ready() && (millis() - sendDataPrevMillis > 3000 || sendDataPrevMillis == 0))
    {
        sendDataPrevMillis = millis();
        soundSensorVal = digitalRead(MIC);


        if (Firebase.RTDB.getInt(&fbdo, lightPath))
        {
            if (fbdo.dataTypeEnum() == firebase_rtdb_data_type_integer)
            {
                int lightValue = fbdo.intData();
                // Set color based on data value
                if (lightValue == 1)
                {
                    // Set color to red
                    setColor(228, 52, 20);
                    Serial.println("Color set to: Red");
                }
                else if (lightValue == 2)
                {
                    // Set color to orange
                    setColor(255, 87, 51);
                    Serial.println("Color set to: Orange");
                }
                else if (lightValue == 3)
                {
                    // Set color to yellow
                    setColor(255, 201, 34);
                    Serial.println("Color set to: Yellow");
                }
                else if (lightValue == 4)
                {
                    // Set color to white
                    setColor(255, 255, 255);
                    Serial.println("Color set to: White");
                }
                else if (lightValue == 5)
                {
                    // Set color to white
                    setColor(0, 0, 0);
                    Serial.println("Color set to: None/Off");
                }
                else
                {
                    Serial.println("Invalid data value!");
                }
            }
            else
            {
                Serial.println("FAILED: " + fbdo.errorReason());
            }
        }
        else
        {
            Serial.println(fbdo.errorReason());
        }
        
        //delay(1000);
        
        if (Firebase.RTDB.getInt(&fbdo, humidifierPath))
        {
            if (fbdo.dataTypeEnum() == firebase_rtdb_data_type_integer)
            {
                int humidifierValue = fbdo.intData();
                // Set color based on data value
                if (humidifierValue == 0)
                {
                    digitalWrite(relay, LOW);
                    Serial.println("Humidifier is turned off.");
                }
                else if (humidifierValue == 1)
                {
                    Serial.println("Humidifier is turned on.");
                    digitalWrite(relay, HIGH);
                }
                else
                {
                    Serial.println("FAILED: " + fbdo.errorReason());
                    Serial.println("Invalid data value!");
                }
            }
        }
        count++;

        if (soundSensorVal == 0) {
        Serial.println("Sound Detected");  // print serial monitor "ON"
      } else if (soundSensorVal == 1) {
        Serial.println("No Sound Detected");  // print serial monitor "OFF"
      }
      if (Firebase.RTDB.setInt(&fbdo, soundSensorPath, soundSensorVal)) {
        Serial.println(soundSensorVal);
      } else {
        Serial.println("FAILED: " + fbdo.errorReason());
      }
    }
}

void setColor(uint8_t red, uint8_t green, uint8_t blue)
{
    // Set the color of all NeoPixels
    for (int i = 0; i < NUMPIXELS; i++)
    {
        pixels.setPixelColor(i, red, green, blue);  
    }
    // Update the NeoPixel strip to display the color
    pixels.show();
}