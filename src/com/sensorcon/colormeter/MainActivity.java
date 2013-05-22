package com.sensorcon.colormeter;

import java.util.EventObject;

import com.sensorcon.sdhelper.ConnectionBlinker;
import com.sensorcon.sdhelper.SDHelper;
import com.sensorcon.sdhelper.SDStreamer;
import com.sensorcon.sensordrone.Drone;
import com.sensorcon.sensordrone.Drone.DroneEventListener;
import com.sensorcon.sensordrone.Drone.DroneStatusListener;

import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.os.Bundle;
import android.os.Handler;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
	
	private int lux;
	private int red;
	private int green;
	private int blue;
	
	private TextView tv_r;
	private TextView tv_g;
	private TextView tv_b;
	private TextView tv_lux;
	private TextView tvConnected;
	private TextView tvNotConnected;
	private ImageButton streamButtonOn;
	private ImageButton streamButtonOff;
	private ImageButton ssButtonOn;
	private ImageButton ssButtonOff;
	private ImageView lensClosed;
	private ImageView lensOpen;
	private ImageView ledOff;
	private ImageView ledOn;
	public AlertInfo myInfo;
	
	private SoundPool shutterSound;
	private int soundId;
	private boolean loaded;
	private AudioManager am;
	
	private boolean streamMode;
	private boolean ledIsOn;
	private int count;
	
	private Handler myHandler = new Handler();
	private Handler shutterHandler = new Handler();
	private Handler ledHandler = new Handler();
	/*
	 * Sensordone variables
	 */
	protected Drone myDrone;
	public Storage box;
	public SDHelper myHelper;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		tv_r = (TextView)findViewById(R.id.r);
		tv_g = (TextView)findViewById(R.id.g);
		tv_b = (TextView)findViewById(R.id.b);
		tv_lux = (TextView)findViewById(R.id.value_lux);
		tvConnected = (TextView)findViewById(R.id.label_connected);
		tvNotConnected = (TextView)findViewById(R.id.label_not_connected);
		
		lensClosed = (ImageView)findViewById(R.id.lens_closed);
		lensOpen = (ImageView)findViewById(R.id.lens_open);
		ledOff = (ImageView)findViewById(R.id.led_off);
		ledOn = (ImageView)findViewById(R.id.led_on);
		
		ssButtonOn = (ImageButton)findViewById(R.id.snapshot_on);
		ssButtonOff = (ImageButton)findViewById(R.id.snapshot_off);
		streamButtonOn = (ImageButton)findViewById(R.id.stream_on);
		streamButtonOff = (ImageButton)findViewById(R.id.stream_off);
		
		lensOpen.setVisibility(View.GONE);
		ssButtonOn.setVisibility(View.GONE);
		streamButtonOn.setVisibility(View.GONE);
		ledOn.setVisibility(View.GONE);
		tvConnected.setVisibility(View.GONE);
		
		streamMode = false;
		ledIsOn = false;
		count = 0;
		
		myInfo = new AlertInfo(this);
		
		shutterSound = new SoundPool(10, AudioManager.STREAM_ALARM, 0);
		shutterSound.setOnLoadCompleteListener(new OnLoadCompleteListener() {
			@Override
			public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
				loaded = true;
			}
		});
		soundId = shutterSound.load(this, R.raw.shutter, 1);
		am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		
		ssButtonOff.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if(event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
					ssButtonDown();
				}
				else if(event.getAction() == android.view.MotionEvent.ACTION_UP) {
					ssButtonUp();
				}
				return false;
			}
		});
		
		streamButtonOff.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				if(event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
					streamButtonDown();
				}
				else if(event.getAction() == android.view.MotionEvent.ACTION_UP) {
					streamButtonUp();
				}
				return false;
			}
		});
		
		myDrone = new Drone();
		box = new Storage(this);
		myHelper = new SDHelper();
	}
	
	public void ssButtonDown() {
		snapshot();
		ssButtonOn.setVisibility(View.VISIBLE);
		ssButtonOff.setVisibility(View.GONE);
	}
	
	public void ssButtonUp() {
		ssButtonOn.setVisibility(View.GONE);
		ssButtonOff.setVisibility(View.VISIBLE);
	}
	
	public void streamButtonDown() {
		streamButtonOn.setVisibility(View.VISIBLE);
		streamButtonOff.setVisibility(View.GONE);
		
		if(!streamMode) {
			Log.d("chris", "Stream");
			stream();
		}
	}
	
	public void streamButtonUp() {
		streamButtonOn.setVisibility(View.GONE);
		streamButtonOff.setVisibility(View.VISIBLE);
	}
	
	/**
	 * Performs a single tick
	 * 
	 * @return	True if successful
	 */
	public boolean shutter() {
		float volume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
		float max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		volume = volume/max;
		
		if(loaded) {
			shutterSound.play(soundId, volume, volume, 1, 0, 1f);
			return true;
		}
		else {
			return false;
		}
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		if (isFinishing()) {
			// Try and nicely shut down
			doOnDisconnect();
			// A brief delay
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			// Unregister the listener
			myDrone.unregisterDroneEventListener(box.droneEventListener);
			myDrone.unregisterDroneStatusListener(box.droneStatusListener);

		} else { 
			//It's an orientation change.
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item){
		switch(item.getItemId()){
		case R.id.disconnect:
			// Only disconnect if it's connected
			if (myDrone.isConnected) {
				// Run our routine of things to do on disconnect
				doOnDisconnect();
			} else {
			}
			break;
		case R.id.connect:
			myHelper.scanToConnect(myDrone, MainActivity.this , this, false);
			break;
		case R.id.reconnect:
			if (!myDrone.isConnected) {
				// This option is used to re-connect to the last connected MAC
				if (!myDrone.lastMAC.equals("")) {
					if (!myDrone.btConnect(myDrone.lastMAC)) {
						myInfo.connectFail();
					}
				} else {
					// Notify the user if no previous MAC was found.
					quickMessage("Last MAC not found... Please scan");
				} 
			} else {
				quickMessage("Already connected...");
			}
			break;
		}
		return true;
	}
	
	public void stream() {
		streamMode = true;
		myHandler.post(displayRGCBRunnable);
		ledHandler.post(ledRunnable);
	}
	
	public void snapshot() {
		streamMode = false;
		myHandler.post(displayRGCBRunnable);
		shutterHandler.post(shutterRunnable);
	}
	
	/**
	 * Shows a simple message on the screen
	 * 
	 * @param msg	Message to be displayed
	 */
	public void quickMessage(final String msg) {
		this.runOnUiThread(new Runnable() {

			@Override
			public void run() {
				Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
			}
		});
	}

	/**
	 * Things to do when drone is disconnected
	 */
	public void doOnDisconnect() {

		// Shut off any sensors that are on
		this.runOnUiThread(new Runnable() {
			
			@Override
			public void run() {
				
				tvConnected.setVisibility(View.GONE);
				tvNotConnected.setVisibility(View.VISIBLE);
				
				if(streamMode) {
					streamMode = false;
				}

				// Turn off myBlinker
				box.myBlinkerR.disable();
				box.myBlinkerG.disable();
				box.myBlinkerB.disable();

				// Make sure the LEDs go off
				if (myDrone.isConnected) {
					myDrone.setLEDs(0, 0, 0);
				}

				// Only try and disconnect if already connected
				if (myDrone.isConnected) {
					myDrone.disconnect();
				}
			}
		});
	}
	
	public Runnable displayRGCBRunnable = new Runnable() {

		@Override
		public void run() {
			if(myDrone.isConnected) {
				tv_r.setText("R: " + Integer.toString(red));
				tv_g.setText("G: " + Integer.toString(green));
				tv_b.setText("B: " + Integer.toString(blue));
				tv_lux.setText(Integer.toString(lux));
				
				if(streamMode) {
					myHandler.postDelayed(this, 1000);
				}
				else {
					myHandler.removeCallbacksAndMessages(null);
				}
			}
			else {
				myHandler.removeCallbacksAndMessages(null);
			}
		}
	};
	
	public Runnable shutterRunnable = new Runnable() {

		@Override
		public void run() {
			
			if(count == 0) {
				lensClosed.setVisibility(View.GONE);
				lensOpen.setVisibility(View.VISIBLE);
				count++;
				shutterHandler.postDelayed(this, 75);
				Log.d("chris", "Lens Open");
				shutter();
			}
			else if(count == 1) {
				lensClosed.setVisibility(View.VISIBLE);
				lensOpen.setVisibility(View.GONE);
				shutterHandler.removeCallbacksAndMessages(null);
				count = 0;
				
				Log.d("chris", "Lens Closed");
			}
		}
	};
	
	public Runnable ledRunnable = new Runnable() {

		@Override
		public void run() {
			if(myDrone.isConnected) {
				if(streamMode) {
					lensOpen.setVisibility(View.VISIBLE);
					lensClosed.setVisibility(View.GONE);
					
					ledHandler.postDelayed(this, 750);
					
					if(ledIsOn) {
						ledIsOn = false;
						ledOn.setVisibility(View.GONE);
						ledOff.setVisibility(View.VISIBLE);
					}
					else {
						ledIsOn = true;
						ledOn.setVisibility(View.VISIBLE);
						ledOff.setVisibility(View.GONE);
					}
				}
				else {
					if(ledIsOn) {
						ledIsOn = false;
						ledOn.setVisibility(View.GONE);
						ledOff.setVisibility(View.VISIBLE);
					}
					ledHandler.removeCallbacksAndMessages(null);
				}
			}
			else {
				ledHandler.removeCallbacksAndMessages(null);
			}
		}
	};
	
	/*
	 * Because Android will destroy and re-create things on events like orientation changes,
	 * we will need a way to store our objects and return them in such a case. 
	 * 
	 * A simple and straightforward way to do this is to create a class which has all of the objects
	 * and values we want don't want to get lost. When our orientation changes, it will reload our
	 * class, and everything will behave as normal! See onRetainNonConfigurationInstance in the code
	 * below for more information.
	 * 
	 * A lot of the GUI set up will be here, and initialized via the Constructor
	 */
	public final class Storage {

		// A ConnectionBLinker from the SDHelper Library
		public ConnectionBlinker myBlinkerR;
		public ConnectionBlinker myBlinkerG;
		public ConnectionBlinker myBlinkerB;

		// Holds the sensor of interest - the CO precision sensor
		public int sensor;

		// Our Listeners
		public DroneEventListener droneEventListener;
		public DroneStatusListener droneStatusListener;
		public String MAC = "";

		// GUI variables
		public TextView statusView;
		public TextView tvConnectionStatus;
		public TextView tvConnectInfo;

		// Streams data from sensor
		public SDStreamer streamer;

		public Storage(Context context) {

			// Initialize sensor
			sensor = myDrone.QS_TYPE_RGBC;

			// This will Blink our Drone, once a second, Blue
			myBlinkerR = new ConnectionBlinker(myDrone, 1000, 255, 0, 0);
			myBlinkerG = new ConnectionBlinker(myDrone, 1000, 0, 255, 0);
			myBlinkerB = new ConnectionBlinker(myDrone, 1000, 0, 0, 255);

			streamer = new SDStreamer(myDrone, sensor);

			/*
			 * Let's set up our Drone Event Listener.
			 * 
			 * See adcMeasured for the general flow for when a sensor is measured.
			 * 
			 */
			droneEventListener = new DroneEventListener() {

				@Override
				public void connectEvent(EventObject arg0) {

					quickMessage("Connected!");

					streamer.enable();
					myDrone.quickEnable(sensor);

					// Flash teh LEDs green
					myHelper.flashLEDs(myDrone, 3, 100, 0, 0, 22);
					// Turn on our blinker
					myBlinkerG.enable();
					myBlinkerG.run();
					
					tvNotConnected.setVisibility(View.GONE);
					tvConnected.setVisibility(View.VISIBLE);
				}


				@Override
				public void connectionLostEvent(EventObject arg0) {
					// Turn off the blinker
					myBlinkerR.disable();
					myBlinkerG.disable();
					myBlinkerB.disable();
					
					quickMessage("Connection lost! Trying to re-connect!");

					// Try to reconnect once, automatically
					if (myDrone.btConnect(myDrone.lastMAC)) {
						// A brief pause
						try {
							Thread.sleep(500);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						
					} else {
						quickMessage("Re-connect failed");
						doOnDisconnect();
					}
				}

				@Override
				public void disconnectEvent(EventObject arg0) {
					quickMessage("Disconnected!");
				}

				@Override
				public void humidityMeasured(EventObject arg0) {
				}
				
				@Override
				public void temperatureMeasured(EventObject arg0) {
				}
				
				@Override
				public void rgbcMeasured(EventObject arg0) {
					if (myDrone.rgbcLux >= 0) {
						lux = (int)myDrone.rgbcLux;
					}
					else {
						lux = 0;
					}
					
					if (myDrone.rgbcRedChannel >= 0) {
						red = (int)myDrone.rgbcRedChannel;
					}
					else {
						red = 0;
					}
					
					if (myDrone.rgbcGreenChannel >= 0) {
						green = (int)myDrone.rgbcGreenChannel;
					}
					else {
						green = 0;
					}
					
					if (myDrone.rgbcBlueChannel >= 0) {
						blue = (int)myDrone.rgbcBlueChannel;
					}
					else {
						blue = 0;
					}
					
					streamer.streamHandler.postDelayed(streamer, 100);
					
				}
				
				/*
				 * Unused events
				 */
				@Override
				public void customEvent(EventObject arg0) {}
				@Override
				public void adcMeasured(EventObject arg0) {}
				@Override
				public void precisionGasMeasured(EventObject arg0) {}
				@Override
				public void altitudeMeasured(EventObject arg0) {}
				@Override
				public void capacitanceMeasured(EventObject arg0) {}
				@Override
				public void i2cRead(EventObject arg0) {}
				@Override
				public void irTemperatureMeasured(EventObject arg0) {}
				@Override
				public void oxidizingGasMeasured(EventObject arg0) {}
				@Override
				public void pressureMeasured(EventObject arg0) {}
				@Override
				public void reducingGasMeasured(EventObject arg0) {}
				
				@Override
				public void uartRead(EventObject arg0) {}
				@Override
				public void unknown(EventObject arg0) {}
				@Override
				public void usbUartRead(EventObject arg0) {}
			};

			/*
			 * Set up our status listener
			 * 
			 * see adcStatus for the general flow for sensors.
			 */
			droneStatusListener = new DroneStatusListener() {

				@Override
				public void humidityStatus(EventObject arg0) {
					
				}
				@Override
				public void temperatureStatus(EventObject arg0) {
					
				}
				
				@Override
				public void rgbcStatus(EventObject arg0) {
					streamer.run();
				}

				/*
				 * Unused statuses
				 */
				@Override
				public void adcStatus(EventObject arg0) {}
				@Override
				public void altitudeStatus(EventObject arg0) {}
				@Override
				public void batteryVoltageStatus(EventObject arg0) {}
				@Override
				public void capacitanceStatus(EventObject arg0) {}
				@Override
				public void chargingStatus(EventObject arg0) {}
				@Override
				public void customStatus(EventObject arg0) {}
				@Override
				public void precisionGasStatus(EventObject arg0) {}
				@Override
				public void irStatus(EventObject arg0) {}
				@Override
				public void lowBatteryStatus(EventObject arg0) {}
				@Override
				public void oxidizingGasStatus(EventObject arg0) {}
				@Override
				public void pressureStatus(EventObject arg0) {}
				@Override
				public void reducingGasStatus(EventObject arg0) {}
				@Override
				public void unknownStatus(EventObject arg0) {}
			};

			// Register the listeners
			myDrone.registerDroneEventListener(droneEventListener);
			myDrone.registerDroneStatusListener(droneStatusListener);
		}
	}
}
