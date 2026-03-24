package rest;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import implementation.Ads1115;

@RestController
@RequestMapping(path = "battery")
public class Telemetry {

	@Autowired
	private Ads1115 adc;

	@GetMapping(path = "volts/{channel}", consumes = "application/json", produces = "application/json")
	public ResponseEntity<Double> getBatteryVolts (@PathVariable("channel") Integer channel){
		try {
			return new ResponseEntity<Double>(adc.readVolts(channel),HttpStatus.OK);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return null;
	}
}