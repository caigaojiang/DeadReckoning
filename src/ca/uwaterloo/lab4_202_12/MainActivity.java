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
	static int step = 0;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		if (savedInstanceState == null) {
			getFragmentManager().beginTransaction()
					.add(R.id.container, new PlaceholderFragment()).commit();
		}
		mv = new MapView(this, 1000, 1000, 40, 40);
		map = MapLoader.loadMap(getExternalFilesDir(null), "E2-3344-W2015.svg");
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

			((Button) rootView.findViewById(R.id.calibrate))
					.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							calibrateFactor = uncalibrate;
						}
					});

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
			lmain.addView(displacementTV[0]);
			lmain.addView(displacementTV[1]);
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
						updateLocation(mv,
								(float) Math.cos(Math.toRadians((calibrate))),
								(float) Math.sin(Math.toRadians((calibrate))));
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

	// A list that represent all the points necessary to draw the path
	static List<PointF> path = new ArrayList<PointF>();
	// Starting and ending points
	static PointF startingPoint;
	static PointF endingPoint;
	static PointF userLocation;
	// Two arbitrary points that help us to connect path
	static PointF intermediate1;
	static PointF intermediate2;
	static PointF intermediate3;

	@Override
	public void originChanged(MapView source, PointF loc) {
		startingPoint = checkWall(loc);
		userLocation = new PointF(startingPoint.x, startingPoint.y);
		source.setUserPoint(userLocation);
		if (endingPoint != null) {
			drawPath();
		}
	}

	@Override
	public void destinationChanged(MapView source, PointF dest) {
		endingPoint = checkWall(dest);
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
		if (point.x < 2.19f || point.x > 24.3f || obstacle1(point)
				|| obstacle2(point) || obstacle3(point) || obstacle4(point)
				|| obstacle5(point) || obstacle6(point) || obstacle7(point)) {
			point.x = -1;
			toast.show();
		}
		if (point.y < 2.18f || point.y > 21) {
			point.y = -1;
			toast.show();
		}
		return point;
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
		if ((start.x > 3.77 && start.x < 10.08 && start.y < 19.26)
				&& (end.x > 3.77 && end.x < 10.08 && end.y < 19.26))
			return true;
		if ((start.x > 13.19 && start.x < 13.9 && start.y < 19.26)
				&& (end.x > 13.19 && end.x < 13.9 && end.y < 19.26))
			return true;
		if ((start.x > 17.2 && start.x < 24.3 && start.y < 19.26)
				&& (end.x > 17.2 && end.x < 24.3 && end.y < 19.26))
			return true;
		return false;
	}

	/**
	 * An algorithm that draw the path on the map by adding points to the path
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
				if (inSpecialCorner(startingPoint)
						&& inSpecialCorner(endingPoint)) {
					// if both points are in special corner
					intermediate1 = new PointF(21f, startingPoint.y);
					intermediate2 = new PointF(21f, endingPoint.y);
					path.clear();
					path.add(startingPoint);
					path.add(intermediate1);
					path.add(intermediate2);
					path.add(endingPoint);
					mv.setUserPath(path);

				} else if (inSpecialCorner(startingPoint)) {
					// if starting point is in special corner
					path.clear();
					intermediate1 = new PointF(21f, startingPoint.y);
					intermediate2 = new PointF(21f, 18.5f);
					intermediate3 = new PointF(endingPoint.x, 18.5f);
					path.add(startingPoint);
					path.add(intermediate1);
					path.add(intermediate2);
					path.add(intermediate3);
					path.add(endingPoint);
					mv.setUserPath(path);
				} else if (inSpecialCorner(endingPoint)) {
					// if ending point is in special corner
					path.clear();
					intermediate1 = new PointF(startingPoint.x, 18.5f);
					intermediate2 = new PointF(21f, 18.5f);
					intermediate3 = new PointF(21f, endingPoint.y);
					path.add(startingPoint);
					path.add(intermediate1);
					path.add(intermediate2);
					path.add(intermediate3);
					path.add(endingPoint);
					mv.setUserPath(path);
				} else {
					// Connect starting point, two intermediate points
					// and ending point in order to create a path
					intermediate1 = new PointF(startingPoint.x, 18.5f);
					intermediate2 = new PointF(endingPoint.x, 18.5f);
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

	public boolean inSpecialCorner(PointF point) {
		if (point.x > 22.83)
			return true;
		return false;
	}

	public static void updateLocation(MapView source, float y, float x) {
		if (y > 0.5) {
			y = 1;
			x = 0;
		}
		if (y < -0.5) {
			y = -1;
			x = 0;
		}
		if (x > 0.5) {
			x = 1;
			y = 0;
		}
		if (x < -0.5) {
			x = -1;
			y = 0;
		}
		if (userLocation != null) {
			userLocation.x += x;
			userLocation.y += y;
			if (userLocation.x < 2.19f || userLocation.x > 24.3f
					|| obstacle1(userLocation) || obstacle2(userLocation)
					|| obstacle3(userLocation) || obstacle4(userLocation)
					|| obstacle5(userLocation) || obstacle6(userLocation)
					|| obstacle7(userLocation) || userLocation.y < 2.18f
					|| userLocation.y > 21) {
				userLocation.x -= x;
				userLocation.y -= y;
			}
			source.setUserPoint(userLocation);
		}
	}

	/**
	 * 
	 * @param point
	 *            either the starting point or the ending point
	 * @return true if user put a point on desk #1. Otherwise, false
	 */
	public static boolean obstacle1(PointF point) {
		if (point.x > 2.19 && point.x < 3.77 && point.y < 17.5)
			return true;
		return false;
	}

	/**
	 * 
	 * @param point
	 *            either the starting point or the ending point
	 * @return true if user put a point on desk #2. Otherwise, false
	 */
	public static boolean obstacle2(PointF point) {
		if (point.x > 10.08 && point.x < 13.19 && point.y < 17.5)
			return true;
		return false;
	}

	/**
	 * 
	 * @param point
	 *            either the starting point or the ending point
	 * @return true if user put a point on desk #3. Otherwise, false
	 */
	public static boolean obstacle3(PointF point) {
		if (point.x > 13.9 && point.x < 17.2 && point.y < 17.5)
			return true;
		return false;
	}

	/**
	 * 
	 * @param point
	 *            either the starting point or the ending point
	 * @return true if user put a point on desk #3. Otherwise, false
	 */
	public static boolean obstacle4(PointF point) {
		if (point.x > 20.7 && point.y < 4.4)
			return true;
		return false;
	}

	/**
	 * 
	 * @param point
	 *            either the starting point or the ending point
	 * @return true if user put a point on desk #3. Otherwise, false
	 */
	public static boolean obstacle5(PointF point) {
		if (point.x > 22.83 && point.y < 19.24 && point.y > 6.99)
			return true;
		return false;
	}

	/**
	 * 
	 * @param point
	 *            either the starting point or the ending point
	 * @return true if user put a point on desk #3. Otherwise, false
	 */
	public static boolean obstacle6(PointF point) {
		if (point.x > 21.5 && point.y > 20.1)
			return true;
		return false;
	}

	/**
	 * 
	 * @param point
	 *            either the starting point or the ending point
	 * @return true if user put a point on desk #3. Otherwise, false
	 */
	public static boolean obstacle7(PointF point) {
		if (point.x > 4.79 && point.x < 19.7 && point.y > 19.26)
			return true;
		return false;
	}
}
