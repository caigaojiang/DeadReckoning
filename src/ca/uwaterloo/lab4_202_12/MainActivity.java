package ca.uwaterloo.lab4_202_12;

import java.util.ArrayList;
import java.util.List;

import mapper.MapLoader;
import mapper.MapView;
import mapper.NavigationalMap;
import mapper.PositionListener;
import android.app.Activity;
import android.app.Fragment;
import android.graphics.PointF;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity implements PositionListener {

	static MapView mv;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		if (savedInstanceState == null) {
			getFragmentManager().beginTransaction()
					.add(R.id.container, new PlaceholderFragment()).commit();
		}
		mv = new MapView(this, 1000, 1000, 52, 52);
		mv.addListener(this);
		registerForContextMenu(mv);
		NavigationalMap map = MapLoader.loadMap(getExternalFilesDir(null),
				"Lab-room-peninsula.svg");
		mv.setMap(map);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		mv.onCreateContextMenu(menu, v, menuInfo);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		return super.onContextItemSelected(item)
				|| mv.onContextItemSelected(item);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * A placeholder fragment containing a simple view.
	 */
	public static class PlaceholderFragment extends Fragment {

		private int step = 0;
		// All in degrees because people are used
		// to measure things in degrees
		private float uncalibrate = 0;
		private float calibrate = 0;
		// displacement[0]: steps with respect to north
		// displacement[1]: steps with respect to east
		private float[] displacement = new float[2];
		private float calibrateFactor = 0;
		private float orientation[] = new float[3];
		private float[] gravity;
		private float[] magnetic;

		public PlaceholderFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_main, container,
					false);
			// Declare TextView
			TextView directionTV = new TextView(rootView.getContext());
			TextView stepTV = new TextView(rootView.getContext());
			TextView[] displacementTV = new TextView[2];
			for (int i = 0; i < displacementTV.length; i++) {
				displacementTV[i] = new TextView(rootView.getContext());
			}

			// Declare sensor manager and linear layout
			SensorManager sensorManager = (SensorManager) rootView.getContext()
					.getSystemService(SENSOR_SERVICE);
			LinearLayout lmain = (LinearLayout) rootView.findViewById(R.id.ll);
			lmain.setOrientation(LinearLayout.VERTICAL);

			// Declare sensors
			Sensor accelerometer = sensorManager
					.getDefaultSensor(Sensor.TYPE_GRAVITY);
			Sensor magnetic = sensorManager
					.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
			Sensor stepCounter = sensorManager
					.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

			// Event listener
			SensorEventListener listener = new Listener(directionTV, stepTV,
					displacementTV);
			// Register sensors
			sensorManager.registerListener(listener, accelerometer,
					SensorManager.SENSOR_DELAY_NORMAL);
			sensorManager.registerListener(listener, magnetic,
					SensorManager.SENSOR_DELAY_NORMAL);
			sensorManager.registerListener(listener, stepCounter,
					SensorManager.SENSOR_DELAY_FASTEST);

			// Add View
			lmain.addView(mv);
			return rootView;
		}

		// A listener class that is responsible for all the sensors
		public class Listener implements SensorEventListener {

			private TextView directionTV;
			private TextView stepTV;
			private TextView[] displacementTV;
			private int currentState = 0;
			private int counter = 0;

			public Listener(TextView directionTV, TextView stepTV,
					TextView[] displacementTV) {
				this.directionTV = directionTV;
				this.stepTV = stepTV;
				this.displacementTV = displacementTV;
			}

			@Override
			public void onSensorChanged(SensorEvent event) {
				if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
					// get gravity array
					gravity = event.values;
				}
				if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
					// get magnetic field array
					magnetic = event.values;
				}
				if (gravity != null && magnetic != null) {
					// get the rotation matrix
					float R[] = new float[9];
					boolean success = SensorManager.getRotationMatrix(R, null,
							gravity, magnetic);
					if (success) {
						// get orientation matrix
						SensorManager.getOrientation(R, orientation);
						// extract azimuth and convert it to degree
						uncalibrate = 180 + (float) Math
								.toDegrees((double) orientation[0]);
						// calibrate if the calibrate button is pressed
						calibrate = uncalibrate - calibrateFactor;
						// does not allow negative degree
						if (calibrate < 0) {
							calibrate += 360;
						}
						directionTV.setText(String.format(
								"%.5f degree with respect to N", calibrate));
					}
				}
				if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
					// apply low pass filter
					float[] data = lowpass(event.values, 1, 1000);
					if (stepTaken(data)) {
						step++;
						// calculate x and y component of the displacement
						displacement[0] += (float) Math.cos(Math
								.toRadians((calibrate)));
						displacement[1] += (float) Math.sin(Math
								.toRadians((calibrate)));
					}
					stepTV.setText(String.format("Steps: " + step));
					displacementTV[0].setText(String.format("North: %.4f",
							displacement[0]));
					displacementTV[1].setText(String.format("East: %.4f",
							displacement[1]));
				}
			}

			@Override
			public void onAccuracyChanged(Sensor sensor, int accuracy) {
			}

			public float[] lowpass(float[] in, float dt, float RC) {
				float[] out = new float[in.length];
				float α = dt / (dt + RC);
				out[0] = 0;
				for (int i = 1; i < in.length; i++) {
					out[i] = α * in[i] + (1 - α) * out[i - 1];
				}
				return out;
			}

			/**
			 * 
			 * @param data
			 *            the linear acceleration array
			 * @return return true if a step is taken. Otherwise, false.
			 */
			public boolean stepTaken(float[] data) {
				if (currentState == 0) {
					// if z acceleration exceeds 0.0008, go to state 1
					if (data[2] > 0.0008) {
						currentState = 1;
					}
				}

				if (currentState == 1) {
					counter++;
					// if z acceleration is lower than -0.005
					if (data[2] < -0.005) {
						currentState = 0;
						// check if time interval between two consecutive
						// steps is small (shaking)
						if (counter > 25) {
							counter = 0;
							return true;
						}
					}
				}
				return false;
			}
		}
	}

	static List<PointF> listOfPoint = new ArrayList<PointF>();
	static PointF startingPoint;
	static PointF endingPoint;
	static PointF intermediate;

	@Override
	public void originChanged(MapView source, PointF loc) {
		Toast toast2 = Toast.makeText(getApplicationContext(),
				String.format("%f %f", loc.x, loc.y), Toast.LENGTH_SHORT);
		startingPoint = checkWall(loc);

		source.setUserPoint(startingPoint);
		toast2.show();
	}

	@Override
	public void destinationChanged(MapView source, PointF dest) {
		endingPoint = dest;
		listOfPoint.add(dest);
		mv.setUserPath(listOfPoint);
	}

	public PointF checkWall(PointF startingPoint) {
		Toast toast = Toast
				.makeText(getApplicationContext(),
						"Invalid origin/destination. Please plot again.",
						Toast.LENGTH_SHORT);
		PointF temp = startingPoint;
		// Is is out of the map?
		if (startingPoint.x > 17f || startingPoint.x < 2f) {
			temp.x = -1;
			toast.show();
		}
		if (startingPoint.y < 2.4f) {
			temp.y = -1;
			toast.show();
		}
		return temp;
	}
}
