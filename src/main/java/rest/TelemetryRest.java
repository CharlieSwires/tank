package rest;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import implementation.Ads1115;
import implementation.Telemetry;

@RestController
@RequestMapping(path = "telemetry")
public class TelemetryRest {

	@Autowired
	private Ads1115 adc;
	@Autowired
	private Telemetry telem;

	@GetMapping(path = "volts/{channel}", produces = "application/json")
	public ResponseEntity<Double> getBatteryVolts (@PathVariable("channel") Integer channel){
		try {
			return new ResponseEntity<Double>(adc.readVolts(channel),HttpStatus.OK);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	@GetMapping(path = "cpu-temp", produces = "application/json")
	public ResponseEntity<Double> getTemperature (){
		try {
			return new ResponseEntity<Double>(telem.getTemperature(),HttpStatus.OK);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

}