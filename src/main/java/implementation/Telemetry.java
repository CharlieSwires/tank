package implementation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import Const.Constant;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class Telemetry {
	
	Logger log = LoggerFactory.getLogger(Telemetry.class);
	public static WatchDog watchDogThread = null;
	public AtomicBoolean startTimer = new AtomicBoolean(false);
	private static boolean disabled = true;
	
	public Telemetry() {
		watchDogThread = new WatchDog();
		disabled = !"true".equals(System.getenv("WATCHDOG_ENABLED"))? true : false;
		watchDogThread.start();

	}
	// Watch Dog thread class
	public class WatchDog extends Thread {

		boolean timeout = false;
		@Override
		public void run() {
			while (true) {
				if (startTimer.get()) {
					try {
						// Sleep for 1second
						WatchDog.sleep(10000);
						setTimeout(true);
						log.error("Watchdog timed out!!");
					} catch (InterruptedException e) {
						setTimeout(false);
						log.debug("InterruptedException true");
						continue;
					}

				} else {
					setTimeout(false);
					try {
						WatchDog.sleep(500);
					} catch (InterruptedException e) {
						log.debug("InterruptedException false");
						continue;
					}
				}
			}
		}

		private void setTimeout(boolean b) {
			timeout = b;			
		}
		
		public boolean getTimeout() {
			return timeout;
		}
	}
	public Double getTemperature() {
		try {
			if (!disabled) {
				if (watchDogThread != null) {
					startTimer.set(true);
					watchDogThread.interrupt();
				}
			} else {
				if (watchDogThread != null) {
					startTimer.set(false);
					watchDogThread.interrupt();
				}
			}
			// Execute the command to get the temperature
			String[] commands = { "vcgencmd", "measure_temp" };
			Process process = Runtime.getRuntime().exec(commands);
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

			// Read the command output
			String line = reader.readLine();
			if (line != null && !line.isEmpty()) {
				// Extract the temperature value from the output string
				// The output is in the format: temp=XX.X'C
				String tempString = line.split("=")[1].replace("'C", "");

				// Convert to double, multiply by 10, and then to integer
				Double tempDouble = Double.parseDouble(tempString);
				return tempDouble;
			}
		} catch (IOException e) {
			log.error("Problem getting temperature.");        }
		return (double) Constant.ERROR; 
	}

}
