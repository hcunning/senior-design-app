package edu.mtu.team9.aspirus.anklet;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.UUID;

/**
 * Created by da Ent on 1-11-2015.
 */
public class BluetoothService {

    private static final String TAG = "Bluetooth Service";
    private int state;

    private BluetoothDevice myDevice;
    private BluetoothLinkListener listener;
    private ConnectThread connectThread;
    private ConnectedThread connectedThread;
    private FileOutputStream loggingOutStream;
    private boolean loggingEnabled = false;
    public BluetoothService(BluetoothDevice device, BluetoothLinkListener listener) {
        this.listener = listener;
        myDevice = device;
    }

    public interface BluetoothLinkListener{
        void onStateChange(int state);
        void onDataRecieved(String data);
    }

    public synchronized void connect() {
        Log.d(TAG,"Connecting to: " + myDevice.getName() + " - " + myDevice.getAddress());
        // Start the thread to connect with the given device

        connectThread = new ConnectThread(myDevice);
        connectThread.start();
    }

    public void setLoggingEnabled(FileOutputStream outputStream){
        this.loggingOutStream = outputStream;
        loggingEnabled = true;
    }

    public synchronized void stop() {
        cancelConnectThread();
        cancelConnectedThread();
        if(loggingEnabled){
            try {
                loggingOutStream.flush();
                loggingOutStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            loggingEnabled = false;
        }
    }

    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        Log.d(TAG, "connected to: " + device.getName());

        cancelConnectThread();

        // Start the thread to manage the connection and perform transmissions
        connectedThread = new ConnectedThread(socket);
        connectedThread.start();
        listener.onStateChange(AnkletConst.CONNECTED);
    }

    /**
     * Indicate that the connection attempt failed and notify the UI Activity.
     */
    private void connectionFailed() {
        Log.e(TAG, "Connection Failed");

        cancelConnectThread();
        listener.onStateChange(AnkletConst.CONNECTION_FAILED);
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        Log.e(TAG, "Connection Lost");
        // Send a failure item_message back to the Activity

        cancelConnectedThread();
        listener.onStateChange(AnkletConst.CONNECTION_LOST);
    }

    private void cancelConnectThread() {
        // Cancel the thread that completed the connection
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }
    }

    private void cancelConnectedThread() {
        // Cancel any thread currently running a connection
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }
    }

    public void write(byte[] out) {
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            r = connectedThread;
        }

        // Perform the write unsynchronized
        r.write(out);
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
                tmp = device.createRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) {
                Log.e(TAG, "Create RFcomm socket failed");
            }
            mmSocket = tmp;
        }

        public void run() {
            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                Log.e(TAG, "Unable to connect");
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e(TAG, "Unable to close() socket during connection failure", closeException);
                }
                connectionFailed();
                return;
            }

            synchronized (BluetoothService.this) {
                connectThread = null;
            }

            // Do work to manage the connection (in a separate thread)
            connected(mmSocket, mmDevice);
        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Close() socket failed", e);
            }
        }
    }


    public class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private final InputStreamReader inputStreamReader;

        public ConnectedThread(BluetoothSocket  socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;
            InputStreamReader temp = null;
            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
                temp = new InputStreamReader(tmpIn,"UTF-8");
            } catch (IOException e) {
                Log.e(TAG, "Temp sockets not created", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
            inputStreamReader = temp;
        }

        public void run() {
            Log.i(TAG, "Begin connectedThread");
            byte[] buffer = new byte[16];  // buffer store for the stream
            int bytesRead = 0;

            StringBuilder readMessage = new StringBuilder();

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {

                    char c = (char)inputStreamReader.read();

                    if(loggingEnabled){
                        loggingOutStream.write(c);
                    }else{
                        if(c == '\n'){
                            listener.onDataRecieved(readMessage.toString().trim());
                            readMessage.setLength(0);
                        }else{
                            readMessage.append(c);
                        }
                    }

                } catch (IOException e) {
                    connectionLost();
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);

            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        /* Call this from the main activity to shutdown the connection */
        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);}
        }
    }

}