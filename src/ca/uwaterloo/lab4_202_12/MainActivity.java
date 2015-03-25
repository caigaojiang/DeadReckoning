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
	static NavigationalMap map;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		if (savedInstanceState == null) {
			getFragmentManager().beginTransaction()
					.add(R.id.container, new PlaceholderFragment()).commit();
		}
		mv = new MapView(this, 1000, 1000, 52, 52);
		map = MapLoader.loadMap(getExternalFilesDir(null),
				"Lab-room-peninsula.svg");
		mv.addListener(this);
		registerForContextMenu(mv);
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

	static List<PointF> path = new ArrayList<PointF>();
	// Starting and ending points
	static PointF startingPoint;
	static PointF endingPoint;
	// Two arbitrary points that help us to connect path
	static PointF intermediate1;
	static PointF intermediate2;

	@Override
	public void originChanged(MapView source, PointF loc) {
		startingPoint = checkWall(loc);
		intermediate1 = new PointF(startingPoint.x, 9.4f);
		source.setUserPoint(startingPoint);
		Toast t = Toast.makeText(getApplicationContext(),
				String.format("%f %f", startingPoint.x, startingPoint.y),
				Toast.LENGTH_SHORT);
		t.show();
		if (endingPoint != null) {
			drawPath();
		}
	}

	@Override
	public void destinationChanged(MapView source, PointF dest) {
		endingPoint = checkWall(dest);
		intermediate2 = new PointF(endingPoint.x, 9.4f);
		if (startingPoint != null) {
			drawPath();
		}
	}

	/**
	 * Check if a point is placed correctly
	 * 
	 * @param point
	 *            point of interest. Either the starting point or the ending
	 *            point
	 * @return return a invalid point if a point is not placed correctly.
	 *         Otherwise, return the original point
	 */
	public PointF checkWall(PointF point) {
		// Warning toast
		Toast toast = Toast.makeText(getApplicationContext(),
				"Invalid origin/destination. Please plot again.",
				Toast.LENGTH_SHORT);
		// Is is out of the map or on the desk?
		if (point.x > 17f || point.x < 2f || inDesk1(point) || inDesk2(point)
				|| inDesk3(point)) {
			point.x = -1;
			toast.show();
		}
		if (point.y < 2.4f || point.y > 11.2) {
			point.y = -1;
			toast.show();
		}
		return point;
	}

	/**
	 * 
	 * @param point
	 *            either the starting point or the ending point
	 * @return true if user put a point on desk #1. Otherwise, false
	 */
	public boolean inDesk1(PointF point) {
		if (point.x > 4.35 && point.x < 6.54 && point.y < 8.55)
			return true;
		return false;
	}

	/**
	 * 
	 * @param point
	 *            either the starting point or the ending point
	 * @return true if user put a point on desk #2. Otherwise, false
	 */
	public boolean inDesk2(PointF point) {
		if (point.x > 8.3 && point.x < 10.7 && point.y < 8.55)
			return true;
		return false;
	}

	/**
	 * 
	 * @param point
	 *            either the starting point or the ending point
	 * @return true if user put a point on desk #3. Otherwise, false
	 */
	public boolean inDesk3(PointF point) {
		if (point.x > 12.43 && point.x < 14.68 && point.y < 8.55)
			return true;
		return false;
	}

	/**
	 * A method that detects obstacles between two points (Basically check if
	 * two points are in the same area/section)
	 * 
	 * @param start
	 *            starting point
	 * @param end
	 *            destination
	 * @return true if there is no obstacles between two points. Otherwise,
	 *         false.
	 */
	public boolean noObstacle(PointF start, PointF end) {
		// To check if there is no obstacle
		// we have to check if two points are in the same area
		if (start.x > 2 && start.x < 4.35 && end.x > 2 && end.x < 4.35)
			return true;
		if (start.x > 6.54 && start.x < 8.3 && end.x > 6.54 && end.x < 8.3)
			return true;
		if (start.x > 10.7 && start.x < 12.43 && end.x > 10.7 && end.x < 12.43)
			return true;
		if (start.x > 14.68 && start.x < 17 && end.x > 14.68 && end.x < 17)
			return true;
		if (start.y > 8.55 && start.y < 10.25 && end.y > 8.55
				&& start.y < 10.25)
			return true;
		return false;
	}

	/**
	 * A method that draw the path on the map by adding points to the path
	 * arraylist and using setUserPath()
	 */
	public void drawPath() {
		// Draw no path if one of the two points are not on the map
		if (startingPoint.x == -1 || startingPoint.y == -1
				|| endingPoint.x == -1 || endingPoint.y == -1) {
			path.clear();
			mv.setUserPath(path);
		} else {
			if (noObstacle(startingPoint, endingPoint)) {
				// If there is no obstacle between two points
				// just connect them together
				path.clear();
				path.add(startingPoint);
				path.add(endingPoint);
				mv.setUserPath(path);
			} else {
				// Connect starting point, two intermediate points
				// and ending point in order to create a path
				path.clear();
				path.add(startingPoint);
				path.add(intermediate1);
				path.add(intermediate2);
				path.add(endingPoint);
				mv.setUserPath(path);
			}
		}
	}
}
