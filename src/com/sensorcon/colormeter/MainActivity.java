package com.sensorcon.colormeter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.sensorcon.sensordrone.DroneEventHandler;
import com.sensorcon.sensordrone.DroneEventListener;
import com.sensorcon.sensordrone.DroneEventObject;
import com.sensorcon.sensordrone.DroneStatusListener;
import com.sensorcon.sensordrone.android.Drone;
import com.sensorcon.sensordrone.android.tools.DroneConnectionHelper;
import com.sensorcon.sensordrone.android.tools.DroneQSStreamer;

public class MainActivity extends Activity {
	
	static String LAST_MAC = "LAST_MAC";
	static String DISABLE_INTRO = "DISABLE_INTRO";
	private SharedPreferences preferences;
	
	private int api;
	private final int NEW_API = 0;
	private final int OLD_API = 1;
	
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
	 * Sensordrone variables
	 */
	protected Drone myDrone;
	public DroneConnectionHelper myHelper;



    // Holds the sensor of interest - the CO precision sensor
    public int sensor;

    // Our Listeners
    public DroneEventHandler droneHandler;
    public DroneEventListener droneEventListener;
    public DroneStatusListener droneStatusListener;
    public String MAC = "";

    // GUI variables
    public TextView statusView;
    public TextView tvConnectionStatus;
    public TextView tvConnectInfo;

    // Streams data from sensor
    public DroneQSStreamer streamer;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// Check to see if API supports swipe views and fragments
		if (android.os.Build.VERSION.SDK_INT < 13) {
		    api = OLD_API;
		} else {
			api = NEW_API;
		}
		
		// Initialize SharedPreferences
		preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
				
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
		
		lensOpen.setVisibility(View.INVISIBLE);
		ssButtonOn.setVisibility(View.INVISIBLE);
		streamButtonOn.setVisibility(View.INVISIBLE);
		ledOn.setVisibility(View.INVISIBLE);
		tvConnected.setVisibility(View.INVISIBLE);
		
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
		
		String disableIntro = preferences.getString(DISABLE_INTRO, "");
		
		if(!disableIntro.equals("DISABLE")) {
			showIntroDialog();
		}
		
		myDrone = new Drone();
		myHelper = new DroneConnectionHelper();

        // Initialize sensor
        sensor = myDrone.QS_TYPE_RGBC;

        streamer = new DroneQSStreamer(myDrone, sensor);

        droneHandler = new DroneEventHandler() {
            @Override
            public void parseEvent(DroneEventObject droneEventObject) {

                if (droneEventObject.matches(DroneEventObject.droneEventType.CONNECTED)) {
                    quickMessage("Connected!");

                    myDrone.setLEDs(126, 0, 0);
                    try {
                        Thread.sleep(333);
                    } catch (InterruptedException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                    myDrone.setLEDs(0, 126, 0);
                    try {
                        Thread.sleep(333);
                    } catch (InterruptedException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                    myDrone.setLEDs(0, 0, 126);
                    try {
                        Thread.sleep(333);
                    } catch (InterruptedException e) {
                        e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    }
                    myDrone.setLEDs(0,0,0);

                    streamer.enable();
                    myDrone.quickEnable(sensor);

                    Editor prefEditor = preferences.edit();
                    prefEditor.putString(LAST_MAC, myDrone.lastMAC);
                    prefEditor.commit();

                    tvNotConnected.setVisibility(View.INVISIBLE);
                    tvConnected.setVisibility(View.VISIBLE);
                } else if (droneEventObject.matches(DroneEventObject.droneEventType.CONNECTION_LOST)) {
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
                } else if (droneEventObject.matches(DroneEventObject.droneEventType.DISCONNECTED)) {
                    quickMessage("Disconnected!");
                }  else if (droneEventObject.matches(DroneEventObject.droneEventType.RGBC_MEASURED)) {
                    // TODO
                    streamer.streamHandler.postDelayed(streamer, 100);
                } else if (droneEventObject.matches(DroneEventObject.droneEventType.RGBC_ENABLED)) {
                    streamer.run();
                }


            }// parseEvent
        };

        myDrone.registerDroneListener(droneHandler);
	}
	
	/**
	 * Loads the dialog shown at startup
	 */
	public void showIntroDialog() {

		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setCancelable(false);
		alert.setTitle("Introduction").setMessage("If you are new to the Color Intensity Meter app, you should read through the instructions. To access them, go to the main menu and select Instructions.");
		alert.setPositiveButton("Don't Show Again", new DialogInterface.OnClickListener() {
		        public void onClick(DialogInterface dialog, int which) { 
		        	Editor prefEditor = preferences.edit();
					prefEditor.putString(DISABLE_INTRO, "DISABLE");
					prefEditor.commit();
		        }
		     })
		    .setNegativeButton("Okay", new DialogInterface.OnClickListener() {
		        public void onClick(DialogInterface dialog, int which) { 
		            // do nothing
		        }
		     }).show();
	}
	
	public void ssButtonDown() {
		snapshot();
		ssButtonOn.setVisibility(View.VISIBLE);
		ssButtonOff.setVisibility(View.INVISIBLE);
	}
	
	public void ssButtonUp() {
		ssButtonOn.setVisibility(View.INVISIBLE);
		ssButtonOff.setVisibility(View.VISIBLE);
	}
	
	public void streamButtonDown() {
		streamButtonOn.setVisibility(View.VISIBLE);
		streamButtonOff.setVisibility(View.INVISIBLE);
		
		if(!streamMode) {
			Log.d("chris", "Stream");
			stream();
		}
	}
	
	public void streamButtonUp() {
		streamButtonOn.setVisibility(View.INVISIBLE);
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
		
        // Try and nicely shut down
        doOnDisconnect();
        // A brief delay
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // Unregister the listener
        myDrone.unregisterDroneListener(droneHandler);

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
//			myHelper.scanToConnect(myDrone, MainActivity.this , this, false);
            myHelper.connectFromPairedDevices(myDrone, this);
			break;
		case R.id.reconnect:
			if (!myDrone.isConnected) {
				String prefLastMAC = preferences.getString(LAST_MAC, "");
				// This option is used to re-connect to the last connected MAC
				if (!prefLastMAC.equals("")) {
					if (!myDrone.btConnect(prefLastMAC)) {
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
		case R.id.instructions:
			if(api == NEW_API) {
				Intent myIntent = new Intent(getApplicationContext(), InstructionsActivity.class);
				startActivity(myIntent);
			}
			else {
				Intent myIntent = new Intent(getApplicationContext(), InstructionsActivityOld.class);
				startActivity(myIntent);
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
				
				tvConnected.setVisibility(View.INVISIBLE);
				tvNotConnected.setVisibility(View.VISIBLE);
				
				if(streamMode) {
					streamMode = false;
				}

				
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
				tv_r.setText("R: " + Integer.toString((int)myDrone.rgbcRedChannel));
				tv_g.setText("G: " + Integer.toString((int)myDrone.rgbcGreenChannel));
				tv_b.setText("B: " + Integer.toString((int)myDrone.rgbcBlueChannel));
				tv_lux.setText(Integer.toString((int)myDrone.rgbcLux));
				
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
				lensClosed.setVisibility(View.INVISIBLE);
				lensOpen.setVisibility(View.VISIBLE);
				count++;
				shutterHandler.postDelayed(this, 75);
				Log.d("chris", "Lens Open");
				shutter();
			}
			else if(count == 1) {
				lensClosed.setVisibility(View.VISIBLE);
				lensOpen.setVisibility(View.INVISIBLE);
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
					lensClosed.setVisibility(View.INVISIBLE);
					
					ledHandler.postDelayed(this, 750);
					
					if(ledIsOn) {
						ledIsOn = false;
						ledOn.setVisibility(View.INVISIBLE);
						ledOff.setVisibility(View.VISIBLE);
					}
					else {
						ledIsOn = true;
						ledOn.setVisibility(View.VISIBLE);
						ledOff.setVisibility(View.INVISIBLE);
					}
				}
				else {
					if(ledIsOn) {
						ledIsOn = false;
						ledOn.setVisibility(View.INVISIBLE);
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


}
