package implementation;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

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
	
	public Double getTemperature() {
		try {
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
