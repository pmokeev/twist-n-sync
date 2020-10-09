package com.googleresearch.capturesync.softwaresync;

import android.content.Context;
import android.util.Log;

import com.googleresearch.capturesync.RawSensorInfo;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ImuTimeSyncListener extends Thread {
    private static final String TAG = "ImuTimeSyncListener";
    private boolean running;
    private final DatagramSocket imuTimeSyncSocket;
    private final int imuTimeSyncPort;
    private final Context mContext;
    private final Ticker localClock;

    public ImuTimeSyncListener(Ticker localClock, DatagramSocket imuTimeSyncSocket, int imuTimeSyncPort, Context context) {
        this.localClock = localClock;
        this.imuTimeSyncSocket = imuTimeSyncSocket;
        this.imuTimeSyncPort = imuTimeSyncPort;
        this.mContext = context;
    }

    public void stopRunning() {
        running = false;
    }

    @Override
    public void run() {
        running = true;

        Log.w(TAG, "Starting IMU Time Sync Listener thread.");
        final int longSize = Long.SIZE / Byte.SIZE;

        byte[] buf = new byte[longSize * 3];

        while (running && !imuTimeSyncSocket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                // Listen for recording start messages

                imuTimeSyncSocket.receive(packet);

                // Separate packet string into int method and string payload
                // First 4 bytes is the integer method.
                ByteBuffer packetByteBuffer = ByteBuffer.wrap(packet.getData());
                int method = packetByteBuffer.getInt(); // From first 4 bytes.
                // Rest of the bytes are the payload.
                String payload = new String(packet.getData(), 4, packet.getLength() - 4);

                if (method != SyncConstants.METHOD_MSG_START_RECORDING) {
                    Log.e(
                            TAG,
                            "Received UDP message with incorrect method "
                                    + method
                                    + ", skipping.");
                    continue;
                }

                RawSensorInfo recorder = new RawSensorInfo(mContext);
                recorder.enableSensors(0, 0);
                String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());
                recorder.startRecording(mContext, timeStamp);
                // Recording process
                Log.d(TAG, "Started recording, sleep for 10 seconds");
                Thread.sleep(SyncConstants.SENSOR_REC_PERIOD_MILLIS);
                recorder.stopRecording();
                recorder.disableSensors();

                // File transfer TODO: move to separate method
                Socket sendSocket;
                FileDetails details;
                byte data[];
                try {
                    String path = mContext.getExternalFilesDir(null).getPath() + "/" + timeStamp;
                    // TODO: add gyro and unique filenames!!!!
                    File file = new File(path, "accel_" + timeStamp);
                    Log.d(TAG, "Sensor file opened");
                    sendSocket = new Socket(packet.getAddress(), imuTimeSyncPort);
                    // File Object for accesing file Details
                    Log.d(TAG, "Connected to Server...");
                    data = new byte[2048];
                    details = new FileDetails();
                    details.setDetails(file.getName(), file.length());

                    // Sending file details to the client
                    Log.d(TAG, "Sending file details...");
                    ObjectOutputStream sendDetails = new ObjectOutputStream(sendSocket.getOutputStream());
                    sendDetails.writeObject(details);
                    sendDetails.flush();
                    // Sending File Data
                    Log.d(TAG, "Sending file data...");
                    FileInputStream fileStream = new FileInputStream(file);
                    BufferedInputStream fileBuffer = new BufferedInputStream(fileStream);
                    OutputStream out = sendSocket.getOutputStream();
                    int count;
                    while ((count = fileBuffer.read(data)) > 0) {
                        Log.d(TAG, "Data Sent : " + count);
                        out.write(data, 0, count);
                        out.flush();
                    }
                    out.close();
                    fileBuffer.close();
                    fileStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            } catch (SocketTimeoutException e) {
                // TODO
            } catch (IOException e) {
                if (running || imuTimeSyncSocket.isClosed()) {
                    Log.w(TAG, "Shutdown arrived in middle of a socket receive, ignoring error.");
                } else {
                    throw new IllegalStateException("Socket Receive/Send error: " + e);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Log.w(TAG, "Time Sync Listener thread finished.");

        //while ()
        /*
        byte[] buf = new byte[SyncConstants.SNTP_BUFFER_SIZE];
        while (running && !nptpSocket.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buf, buf.length);
            try {
                // Listen for PTP messages.
                nptpSocket.receive(packet);

                // 2 (B) - Recv UDP message with t0 at time t0'.
                long t0r = localClock.read();

                final int longSize = Long.SIZE / Byte.SIZE;

                if (packet.getLength() != longSize) {
                    Log.e(
                            TAG,
                            "Received UDP message with incorrect packet length "
                                    + packet.getLength()
                                    + ", skipping.");
                    continue;
                }

                // 3 (B) - Send UDP message with t0,t0',t1 at time t1.
                long t1 = localClock.read();
                ByteBuffer buffer = ByteBuffer.allocate(3 * longSize);
                buffer.put(packet.getData(), 0, longSize);
                buffer.putLong(longSize, t0r);
                buffer.putLong(2 * longSize, t1);
                byte[] bufferArray = buffer.array();

                // Send SNTP response back.
                DatagramPacket response =
                        new DatagramPacket(bufferArray, bufferArray.length, packet.getAddress(), nptpPort);
                nptpSocket.send(response);
            } catch (SocketTimeoutException e) {
                // It is normal to time out most of the time, continue.
            } catch (IOException e) {
                if (nptpSocket.isClosed()) {
                    // Stop here if socket is closed.
                    return;
                }
                throw new IllegalStateException("SNTP Thread didn't close gracefully: " + e);
            }
        }
        Log.w(TAG, "SNTP Listener thread finished.");
    */
    }
}
