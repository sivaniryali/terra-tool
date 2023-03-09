package com.example.terratool;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import android.hardware.usb.UsbManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.tensorflow.lite.DataType;

import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.UUID;

import com.example.terratool.ml.Model;


public class MainActivity extends AppCompatActivity {

    Button gallery;
    ImageView imageView;
    TextView result;
    int imageSize = 224;
    static final UUID mUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        gallery = findViewById(R.id.button2);

        result = findViewById(R.id.result);
        imageView = findViewById(R.id.imageView);

        gallery.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent cameraIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(cameraIntent, 1);
            }
        });
    }

    public void sendToArduino(String message){
        UsbManager usbManager = (UsbManager)getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        UsbDevice device;
        UsbDeviceConnection connection;
        if (!usbDevices.isEmpty()) {
            for (java.util.Map.Entry<String,UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                if (deviceVID == 0x2341) {//Arduino Vendor ID
                    connection = usbManager.openDevice(device);
                    connection.bulkTransfer(device.getInterface(0).getEndpoint(0), message.getBytes(StandardCharsets.UTF_8), message.length(), 0);
                }
            }
        }

    }
    public void classifyImage(Bitmap image){
        try {
            Model model = Model.newInstance(getApplicationContext());

            // Creates inputs for reference.
            TensorBuffer inputFeature0 = TensorBuffer.createFixedSize(new int[]{1, 224, 224, 3}, DataType.FLOAT32);
            ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4 * imageSize * imageSize * 3);
            byteBuffer.order(ByteOrder.nativeOrder());

            int[] intValues = new int[imageSize * imageSize];
            image.getPixels(intValues, 0, image.getWidth(), 0, 0, image.getWidth(), image.getHeight());
            int pixel = 0;
            //iterate over each pixel and extract R, G, and B values. Add those values individually to the byte buffer.
            for(int i = 0; i < imageSize; i ++){
                for(int j = 0; j < imageSize; j++){
                    int val = intValues[pixel++]; // RGB
                    byteBuffer.putFloat(((val >> 16) & 0xFF) * (1.f / 1));
                    byteBuffer.putFloat(((val >> 8) & 0xFF) * (1.f / 1));
                    byteBuffer.putFloat((val & 0xFF) * (1.f / 1));
                }
            }

            inputFeature0.loadBuffer(byteBuffer);

            // Runs model inference and gets result.
            Model.Outputs outputs = model.process(inputFeature0);
            TensorBuffer outputFeature0 = outputs.getOutputFeature0AsTensorBuffer();

            float[] confidences = outputFeature0.getFloatArray();
            // find the index of the class with the biggest confidence.
            int maxPos = 0;
            float maxConfidence = 0;
            for (int i = 0; i < confidences.length; i++) {
                if (confidences[i] > maxConfidence) {
                    maxConfidence = confidences[i];
                    maxPos = i;
                }
            }
            String[] classes = {"clay", "sand", "loam", "silt"};
            result.setText(classes[maxPos]);
            //sendToArduino(classes[maxPos]);
            // Releases model resources if no longer used.
            model.close();
        } catch (IOException e) {
            // TODO Handle the exception
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if(resultCode == RESULT_OK){
            if(requestCode == 3){
                assert data != null;
                Bitmap image = (Bitmap) data.getExtras().get("data");
                int dimension = Math.min(image.getWidth(), image.getHeight());
                image = ThumbnailUtils.extractThumbnail(image, dimension, dimension);
                imageView.setImageBitmap(image);

                image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false);
                classifyImage(image);
            }else{
                assert data != null;
                Uri dat = data.getData();
                Bitmap image = null;
                try {
                    image = MediaStore.Images.Media.getBitmap(this.getContentResolver(), dat);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                imageView.setImageBitmap(image);

                image = Bitmap.createScaledBitmap(image, imageSize, imageSize, false);
                classifyImage(image);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @SuppressLint("MissingPermission")
    public void configureSensor(View view) {

        if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED)
        {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 2);
            return;
        }

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        System.out.println(bluetoothAdapter.getBondedDevices());
        BluetoothDevice hc05 = bluetoothAdapter.getRemoteDevice("98:D3:91:FD:47:D7");
        System.out.println(hc05.getName());
        String soilType = String.valueOf(result.getText());
        int soilVal = 400;
        System.out.println("Soil type: "+soilType);
        if(soilType == "clay") soilVal = 300;
        else if (soilType == "loam") soilVal = 350;
        ParcelUuid[] uuids = hc05.getUuids();
        try {
            System.out.println(uuids[0].getUuid());
        } catch (Exception e){
            //Toast.makeText(this, "Bluetooth device is out of range", Toast.LENGTH_SHORT).show();
            System.out.println("Bluetooth device is out of range");
            return;
        }

        BluetoothSocket btSocket = null;
        int counter = 0;
        do {
            try {
                btSocket = hc05.createRfcommSocketToServiceRecord(uuids[0].getUuid());
                System.out.println(btSocket);
                btSocket.connect();
                System.out.println("Hello Hello " + btSocket.isConnected());
            } catch (IOException e) {
                //e.printStackTrace();
            }
            counter++;
        } while (!btSocket.isConnected() && counter < 1);

        try {
            if(btSocket.isConnected()) {
                OutputStream outputStream = btSocket.getOutputStream();
                outputStream.write(soilVal);
            } else {
                //Toast.makeText(this, "Could not connect", Toast.LENGTH_SHORT).show();
                System.out.println("Could not connect");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            btSocket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        Toast.makeText(this, "Sent message to bluetooth "+soilVal, Toast.LENGTH_SHORT).show();
    }
}