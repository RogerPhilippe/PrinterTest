package br.com.philippesis.printertest;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.*;

public class PrintActivity extends AppCompatActivity {

    // will show the statuses like bluetooth open, close or data sent
    private TextView myLabel;

    // will enable user to enter any text to be printed
    private EditText myTextbox;

    private Button openButton;
    private Button sendButton;
    private Button closeButton;

    private BluetoothSocket mmSocket;
    private BluetoothDevice mmDevice;

    // needed for communication to bluetooth device / network
    private OutputStream mmOutputStream;
    private InputStream mmInputStream;

    private byte[] readBuffer;
    private int readBufferPosition;
    private volatile boolean stopWorker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_print);

        try {
            // more codes will be here
            // we are going to have three buttons for specific functions
            openButton = findViewById(R.id.open);
            sendButton = findViewById(R.id.send);
            closeButton = findViewById(R.id.close);

            // text label and input box
            myLabel = findViewById(R.id.label);
            myTextbox = findViewById(R.id.entry);
        }catch(Exception ex) {
            Log.e("[MC1-Test]", ex.getMessage());
        }

        // open bluetooth connection
        openButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    findBT();
                    openBT();
                } catch (IOException ex) {
                    Log.e("[MC1-Test]", ex.getMessage());
                }
            }
        });

        // send data typed by the user to be printed
        sendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    sendData();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });

        // close bluetooth connection
        closeButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                closeBT();
            }
        });

    }

    // this will find a bluetooth printer device
    private void findBT() {

        try {
            // android built in classes for bluetooth operations
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            if(mBluetoothAdapter == null) {
                myLabel.setText(this.getResources().getString(R.string.error_bluetooth_msg));
            }

            if(!mBluetoothAdapter.isEnabled()) {
                Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBluetooth, 0);
            }

            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

            if(pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {

                    Log.v("[MC1-Test]", device.getName());

                    // PR2 is the name of the bluetooth printer device
                    // we got this name from the list of paired devices
                    if (device.getName().equals("PR2-25621346059")) {
                        mmDevice = device;
                        break;
                    }
                }
            }

            myLabel.setText(this.getResources().getString(R.string.bluetooth_found_msg));

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    // tries to open a connection to the bluetooth printer device
    private void openBT() throws IOException {

            // Standard SerialPortService ID
            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
            mmSocket.connect();
            mmOutputStream = mmSocket.getOutputStream();
            mmInputStream = mmSocket.getInputStream();

            beginListenForData();

            myLabel.setText(this.getResources().getString(R.string.bluetooth_opened_msg));

    }

    /*
     * after opening a connection to bluetooth printer device,
     * we have to listen and check if a data were sent to be printed.
     */
    private void beginListenForData() {
        try {
            final Handler handler = new Handler();

            // this is the ASCII code for a newline character
            final byte delimiter = 10;

            stopWorker = false;
            readBufferPosition = 0;
            readBuffer = new byte[1024];

            // specify US-ASCII encoding
            // tell the user data were sent to bluetooth printer device
            Thread workerThread = new Thread(new Runnable() {
                public void run() {

                    while (!Thread.currentThread().isInterrupted() && !stopWorker) {

                        try {

                            int bytesAvailable = mmInputStream.available();

                            if (bytesAvailable > 0) {

                                byte[] packetBytes = new byte[bytesAvailable];
                                int read = mmInputStream.read(packetBytes);
                                Log.v("[MC1-Test]", String.valueOf(read));

                                for (int i = 0; i < bytesAvailable; i++) {

                                    byte b = packetBytes[i];
                                    if (b == delimiter) {

                                        byte[] encodedBytes = new byte[readBufferPosition];
                                        System.arraycopy(
                                                readBuffer, 0,
                                                encodedBytes, 0,
                                                encodedBytes.length
                                        );

                                        // specify US-ASCII encoding
                                        final String data = new String(encodedBytes, Charset.defaultCharset());
                                        readBufferPosition = 0;

                                        // tell the user data were sent to bluetooth printer device
                                        handler.post(new Runnable() {
                                            public void run() {
                                                myLabel.setText(data);
                                            }
                                        });

                                    } else {
                                        readBuffer[readBufferPosition++] = b;
                                    }
                                }
                            }

                        } catch (IOException ex) {
                            stopWorker = true;
                            Log.e("[MC1-Test]", ex.getMessage());
                        }

                    }
                }
            });

            workerThread.start();

        } catch (Exception ex) {
            Log.e("[MC1-Test]", ex.getMessage());
        }
    }

    // this will send text data to be printed by the bluetooth printer
    private void sendData() throws IOException {

        // the text typed by the user
        String msg = myTextbox.getText().toString();
        msg += "\n";

        byte[] cc = new byte[]{0x1B,0x21,0x00};  // 0- normal size text
        byte[] bb = new byte[]{0x1B,0x21,0x08};  // 1- only bold text
        byte[] bb2 = new byte[]{0x1B,0x21,0x20}; // 2- bold with medium text
        byte[] bb3 = new byte[]{0x1B,0x21,0x10}; // 3- bold with large text

        byte[] image = Utils.decodeBitmap(Objects.requireNonNull(loadImageFromAssets("Signature.bmp")));

        mmOutputStream.write("\n".getBytes());
        mmOutputStream.write(bb3);
        mmOutputStream.write("MC1- PEPSICO\n".getBytes());
        mmOutputStream.write("\n".getBytes());
        mmOutputStream.write(bb);
        mmOutputStream.write(msg.getBytes());
        mmOutputStream.write("\n".getBytes());
        mmOutputStream.write("\n".getBytes());
//        mmOutputStream.write(image);
//        mmOutputStream.write("\n".getBytes());
//        mmOutputStream.write("\n".getBytes());
        mmOutputStream.write(cc);
        mmOutputStream.write(
                Calendar.getInstance(new Locale("pt", "BR")).getTime().toString().getBytes());
        mmOutputStream.write("\n".getBytes());
        mmOutputStream.write("Fim da impressao".getBytes());
        mmOutputStream.write("\n".getBytes());
        mmOutputStream.write("\n".getBytes());

        // tell the user data were sent
        myLabel.setText(this.getResources().getString(R.string.send_data_msg));

    }

    // close the connection to bluetooth printer.
    private void closeBT() {
        try {
            stopWorker = true;
            mmOutputStream.close();
            mmInputStream.close();
            mmSocket.close();
            myLabel.setText(this.getResources().getString(R.string.closed_bluetooth_msg));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Bitmap loadImageFromAssets(String imageName) {

        try {
            InputStream ims = getAssets().open(imageName);
            Bitmap mImage = BitmapFactory.decodeStream(ims);
            return mImage;
        } catch (Exception ex) {
            Log.e("", ex.getMessage());
        }
        return null;

    }

}
