package com.example.willhtun.carremote;

import android.app.ActionBar;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.UnsupportedEncodingException;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    // 2 for line           0
    // 0 for collision      1
    // 1 for manual         2
    // 3 for manual motion  3
    int mode = 1;

    //MACROS
    public static final int REQUEST_BLUETOOTH_ENABLE = 1;
    //public static final String CAR_IDENTIFIER = "HC-08";
    public static final String CAR_MAC_ADDR_STRING = "3C:A3:08:94:42:CF";
    public static final String SERVICE_UUID_STRING = "0000ffe0-0000-1000-8000-00805f9b34fb";
    public static final String READ_WRITE_UUID_STRING = "0000ffe1-0000-1000-8000-00805f9b34fb";

    //Required hardware
    private SensorManager sensorManager;
    private Sensor gyroscope;
    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    //Vars for connecting to car and sending/receiving data
    private BluetoothGatt bluetoothGatt;
    private BluetoothDevice car;
    private UUID SERVICE_UUID = UUID.fromString(SERVICE_UUID_STRING);
    private UUID READ_WRITE_UUID = UUID.fromString(READ_WRITE_UUID_STRING);
    private boolean deviceConnected = false;
    private boolean initialized = false;

    private boolean connectionMade = false;

    //Storing amount of rotation in each axis
    private float offsetY = 0, offsetZ = 0;
    private float rotateX, rotateY, rotateZ;

    //OLD_STUFF
    //private TextView displayCurrVal;
    private TextView displayLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Lock orientation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_main);

        // Hide the status bar and action bar
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);
        ActionBar actionBar = getActionBar();
        if (actionBar != null)
            actionBar.hide();

        // Set up sensors
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        //OLD_STUFF
        //displayCurrVal = findViewById(R.id.textView);
        displayLog = findViewById(R.id.debugtext);

        //Register listener for gyroscope to know when user rotates device
        sensorManager.registerListener(gyroscopeListener, gyroscope, sensorManager.SENSOR_DELAY_GAME);

        // Set up buttons
        setUpButtonListeners();
    }

    @Override
    protected void onPause() {
        //Unregister listener so it no longer pays attention to when user rotates device
        sensorManager.unregisterListener(gyroscopeListener);

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        //Register listener again to check for user rotating device
        sensorManager.registerListener(gyroscopeListener, gyroscope, sensorManager.SENSOR_DELAY_GAME);
    }

    private final SensorEventListener gyroscopeListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (mode == 3) {

                rotateX = event.values[0];
                rotateY = event.values[1];
                rotateZ = event.values[2];

                float threshold = gyroscope.getMaximumRange() / 10;

                if (connectionMade && initialized) {
                    if (Math.abs(rotateY) > threshold) {
                        if (rotateY > 0) {
//                    Toast.makeText(getApplicationContext(), "FORWARD", Toast.LENGTH_SHORT).show();
                            displayLog.setText("Rotation Direction - FORWARD");
                            sendData("f");
                        } else {
//                    Toast.makeText(getApplicationContext(), "BACKWARDS", Toast.LENGTH_SHORT).show();
                            displayLog.setText("Rotation Direction - BACKWARDS");
                            sendData("b");
                        }
                    }

                    if (Math.abs(rotateZ) > threshold) {
                        if (rotateZ > 0) {
//                    Toast.makeText(getApplicationContext(), "LEFT", Toast.LENGTH_SHORT).show();
                            displayLog.setText("Rotation Direction - LEFT");
                            sendData("l");
                        } else {
//                    Toast.makeText(getApplicationContext(), "RIGHT", Toast.LENGTH_SHORT).show();
                            displayLog.setText("Rotation Direction - RIGHT");
                            sendData("r");
                        }
                    }
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    };

    public void buttonPairFunc(View view) {
        if (bluetoothAdapter == null)
        {
//            Toast.makeText(getApplicationContext(),"Device does not support Bluetooth.", Toast.LENGTH_SHORT).show();
            displayLog.setText("Device does not support Bluetooth.");
            return;
        }

        if (!bluetoothAdapter.isEnabled())
        {
            Intent enableAdapter = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableAdapter, REQUEST_BLUETOOTH_ENABLE);
            Toast.makeText(getApplicationContext(),"Tap 'Pair' again after enabling bluetooth.", Toast.LENGTH_LONG).show();
        }

        if (bluetoothAdapter.isEnabled())
        {
            connectToCar();
        }
    }

    public void disconnectCar(View view) {
        if (bluetoothGatt == null) {
            return;
        }
        bluetoothGatt.close();
//        bluetoothGatt.disconnect();
        bluetoothGatt = null;
    }

    public void stopCar(View view){
        sendData("s");
    }

    public void sendData(String data)
    {
        BluetoothGattService service = bluetoothGatt.getService(SERVICE_UUID);
        BluetoothGattCharacteristic characteristic = service.getCharacteristic(READ_WRITE_UUID);

        byte[] messageBytes = new byte[0];

        messageBytes = data.getBytes();
        characteristic.setValue(messageBytes);
        bluetoothGatt.writeCharacteristic(characteristic);
    }

    //Simply saves reference to car device or starts discovery mode to discover and then save reference to car
    private void connectToCar()
    {
        //Start bluetooth discovery mode to look for nearby devices (aka look for car)
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_NAME_CHANGED);
        registerReceiver(BTDisReceiver, filter);

        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }

        bluetoothAdapter.startDiscovery();
        Log.i("BTDis", "Start Discovery");
    }

    //Saves reference to car after discovering it first
    private final BroadcastReceiver BTDisReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            //Once new device has been discovered, see if it is the car and save reference to it
            if (BluetoothDevice.ACTION_NAME_CHANGED.equals(action)) {
                //bluetooth device found
                BluetoothDevice tempDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                displayLog.setText("Found device: " + tempDevice.getName());
                Log.d("BTDis", tempDevice.getName());

                if (tempDevice.getAddress().equals(CAR_MAC_ADDR_STRING))
                {
                    car = tempDevice;
                    Log.d("device", "Saving device " + tempDevice.getName() + " Car: " + car.getName());
                    displayLog.setText("Saving device: " + tempDevice.getName());
                    bluetoothAdapter.cancelDiscovery();
                    connectionMade = BTconnect();
                    return;
                }
            }
            else if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action))
            {
                deviceConnected = true;
            }
        }
    };

    public boolean BTconnect()
    {
        if (!deviceConnected)
        {
            bluetoothGatt = car.connectGatt(this, true, gattCallback);
            return true;
        }

        return false;
    }

    BluetoothGattCallback gattCallback = new BluetoothGattCallback()
    {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BLE", "Connected");
                connectionMade = true;
                deviceConnected = true;
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("GATT", "Disconnected from GATT server.");
                deviceConnected = false;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            if (status != BluetoothGatt.GATT_SUCCESS)
            {
                return;
            }

            BluetoothGattService service = gatt.getService(SERVICE_UUID);
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(READ_WRITE_UUID);
            characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            initialized = gatt.setCharacteristicNotification(characteristic, true);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            byte[] messageBytes = characteristic.getValue();
            String messageString = null;

            try {
                messageString = new String(messageBytes, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            displayLog.setText(messageString);
        }

//        @Override
//        public void onCharacteristicRead(BluetoothGatt gatt,
//                                         BluetoothGattCharacteristic characteristic,
//                                         int status) {
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
//            }
//        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

        }
    };

    public void clickLineTracking(View view) {
        switchMode(2);
    }
    public void clickCollision(View view) {
        switchMode(0);
    }
    public void clickManual(View view) {
        switchMode(1);
    }
    public void clickManualMotion(View view) {
        if (mode == 1) {
            switchMode(3);
            ((ImageView) findViewById(R.id.motiontoggle)).setImageResource(R.drawable.ic_motion_on);
            ((TextView) findViewById(R.id.motiontoggletext)).setText("Motion On");
        }
        else if (mode == 3) {
            switchMode(1);
            ((ImageView) findViewById(R.id.motiontoggle)).setImageResource(R.drawable.ic_motion_off);
            ((TextView) findViewById(R.id.motiontoggletext)).setText("Motion Off");
        }
    }

    private void switchMode(int m) {
        switch(m) {
            case 2:
                mode = 2;
                sendData("2");
                ((ImageButton) findViewById(R.id.linetracking)).setImageResource(R.drawable.ic_icon_linetracking_on);
                ((ImageButton) findViewById(R.id.collision)).setImageResource(R.drawable.ic_icon_collision_off);
                ((ImageButton) findViewById(R.id.manual)).setImageResource(R.drawable.ic_icon_manual_off);
                ((ImageButton) findViewById(R.id.forward)).setVisibility(View.INVISIBLE);
                ((ImageButton) findViewById(R.id.backward)).setVisibility(View.INVISIBLE);
                ((ImageButton) findViewById(R.id.right)).setVisibility(View.INVISIBLE);
                ((ImageButton) findViewById(R.id.left)).setVisibility(View.INVISIBLE);
                ((ImageView) findViewById(R.id.motiontoggle)).setVisibility(View.INVISIBLE);
                break;
            case 0:
                mode = 0;
                sendData("0");
                ((ImageButton) findViewById(R.id.linetracking)).setImageResource(R.drawable.ic_icon_linetracking_off);
                ((ImageButton) findViewById(R.id.collision)).setImageResource(R.drawable.ic_icon_collision_on);
                ((ImageButton) findViewById(R.id.manual)).setImageResource(R.drawable.ic_icon_manual_off);
                ((ImageButton) findViewById(R.id.forward)).setVisibility(View.INVISIBLE);
                ((ImageButton) findViewById(R.id.backward)).setVisibility(View.INVISIBLE);
                ((ImageButton) findViewById(R.id.right)).setVisibility(View.INVISIBLE);
                ((ImageButton) findViewById(R.id.left)).setVisibility(View.INVISIBLE);
                ((ImageView) findViewById(R.id.motiontoggle)).setVisibility(View.INVISIBLE);
                break;
            case 1:
                mode = 1;
                sendData("1");
                ((ImageButton) findViewById(R.id.linetracking)).setImageResource(R.drawable.ic_icon_linetracking_off);
                ((ImageButton) findViewById(R.id.collision)).setImageResource(R.drawable.ic_icon_collision_off);
                ((ImageButton) findViewById(R.id.manual)).setImageResource(R.drawable.ic_icon_manual_on);
                ((ImageButton) findViewById(R.id.forward)).setVisibility(View.VISIBLE);
                ((ImageButton) findViewById(R.id.backward)).setVisibility(View.VISIBLE);
                ((ImageButton) findViewById(R.id.right)).setVisibility(View.VISIBLE);
                ((ImageButton) findViewById(R.id.left)).setVisibility(View.VISIBLE);
                ((ImageView) findViewById(R.id.motiontoggle)).setVisibility(View.VISIBLE);
                ((ImageView) findViewById(R.id.motiontoggle)).setImageResource(R.drawable.ic_motion_off);
                ((TextView) findViewById(R.id.motiontoggletext)).setText("Motion Off");
                break;
            case 3:
                mode = 3;
                sendData("3");
                ((ImageButton) findViewById(R.id.linetracking)).setImageResource(R.drawable.ic_icon_linetracking_off);
                ((ImageButton) findViewById(R.id.collision)).setImageResource(R.drawable.ic_icon_collision_off);
                ((ImageButton) findViewById(R.id.manual)).setImageResource(R.drawable.ic_icon_manual_on);
                ((ImageButton) findViewById(R.id.forward)).setVisibility(View.VISIBLE);
                ((ImageButton) findViewById(R.id.backward)).setVisibility(View.VISIBLE);
                ((ImageButton) findViewById(R.id.right)).setVisibility(View.VISIBLE);
                ((ImageButton) findViewById(R.id.left)).setVisibility(View.VISIBLE);
                ((ImageView) findViewById(R.id.motiontoggle)).setVisibility(View.VISIBLE);
                break;
            default:
                mode = 1;
                sendData("1");
                ((ImageButton) findViewById(R.id.linetracking)).setImageResource(R.drawable.ic_icon_linetracking_off);
                ((ImageButton) findViewById(R.id.collision)).setImageResource(R.drawable.ic_icon_collision_off);
                ((ImageButton) findViewById(R.id.manual)).setImageResource(R.drawable.ic_icon_manual_on);
                ((ImageButton) findViewById(R.id.forward)).setVisibility(View.VISIBLE);
                ((ImageButton) findViewById(R.id.backward)).setVisibility(View.VISIBLE);
                ((ImageButton) findViewById(R.id.right)).setVisibility(View.VISIBLE);
                ((ImageButton) findViewById(R.id.left)).setVisibility(View.VISIBLE);
                ((ImageView) findViewById(R.id.motiontoggle)).setVisibility(View.VISIBLE);
                ((ImageView) findViewById(R.id.motiontoggle)).setImageResource(R.drawable.ic_motion_off);
                ((TextView) findViewById(R.id.motiontoggletext)).setText("Motion Off");
                break;
        }
    }

    public void moveForward() {
        if (mode == 1) {
            displayLog.setText("Move Forward");
            sendData("f");
        }
    }
    public void moveBackward() {
        if (mode == 1) {
            displayLog.setText("Move Backward");
            sendData("b");
        }
    }
    public void moveLeft() {
        if (mode == 1) {
            displayLog.setText("Move Left");
            sendData("l");
        }
    }
    public void moveRight() {
        if (mode == 1) {
            displayLog.setText("Move Right");
            sendData("r");
        }
    }
    public void dontMove() {
        if (mode == 1) {
            displayLog.setText("");
            sendData("s");
        }
    }

    private void setUpButtonListeners() {
        ((ImageButton) findViewById(R.id.forward)).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    moveForward();
                    ((ImageButton) findViewById(R.id.forward)).setImageResource(R.drawable.ic_dirbutton_pressed);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    dontMove();
                    ((ImageButton) findViewById(R.id.forward)).setImageResource(R.drawable.ic_dirbutton_unpressed);
                }
                return true;
            }
        });
        ((ImageButton) findViewById(R.id.backward)).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    moveBackward();
                    ((ImageButton) findViewById(R.id.backward)).setImageResource(R.drawable.ic_dirbutton_pressed);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    dontMove();
                    ((ImageButton) findViewById(R.id.backward)).setImageResource(R.drawable.ic_dirbutton_unpressed);
                }
                return true;
            }
        });
        ((ImageButton) findViewById(R.id.right)).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    moveRight();
                    ((ImageButton) findViewById(R.id.right)).setImageResource(R.drawable.ic_dirbuttonlr_pressed);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    dontMove();
                    ((ImageButton) findViewById(R.id.right)).setImageResource(R.drawable.ic_dirbuttonlr_unpressed);
                }
                return true;
            }
        });
        ((ImageButton) findViewById(R.id.left)).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    moveLeft();
                    ((ImageButton) findViewById(R.id.left)).setImageResource(R.drawable.ic_dirbuttonlr_pressed);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    dontMove();
                    ((ImageButton) findViewById(R.id.left)).setImageResource(R.drawable.ic_dirbuttonlr_unpressed);
                }
                return true;
            }
        });
    }
}
