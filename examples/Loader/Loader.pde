import com.rngtng.arduinoloader.*;

import processing.serial.*;

void setup() {
  size(400,400);

  String portName = Serial.list()[0];
  int baudRate = 19200;
  int pageSize = 128;

  //String loadPath = selectInput(); 
    
  try {
    int imagesize = ArduinoLoader.upload( "/Users/ted/Sites/java/arduinoloader/examples/Loader/blink.hex", portName, baudRate, pageSize);
    System.out.println("Completed, " + imagesize + " bytes uploaded");
  }
  catch(Exception e ) {
    println( e.getMessage());
  }


}

void draw() {
  background(0);
}




