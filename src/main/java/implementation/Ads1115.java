package implementation;


import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CConfig;
import com.pi4j.io.i2c.I2CProvider;

import implementation.Telemetry.WatchDog;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class Ads1115 {
	Logger log = LoggerFactory.getLogger(Ads1115.class);

	// ADS1115 register pointer values
	private static final int REG_CONVERSION = 0x00;
	private static final int REG_CONFIG     = 0x01;

	// Common default I2C address if ADDR is tied to GND
	private static final int ADS1115_ADDR   = 0x48;
	private static final int I2C_BUS        = 1;
	private static I2C i2c2 = null;
	public static WatchDog watchDogThread = null;
	public AtomicBoolean startTimer = new AtomicBoolean(false);
	private static boolean disabled = true;

	@Autowired
	public Ads1115() throws InterruptedException {
		Context pi4j = Pi4J.newAutoContext();
		watchDogThread = new WatchDog();
		disabled = !"true".equals(System.getenv("WATCHDOG_ENABLED"))? true : false;

		I2CProvider provider = pi4j.provider("linuxfs-i2c");
		I2CConfig config = I2C.newConfigBuilder(pi4j)
				.id("ADS1115")
				.name("ADS1115 ADC")
				.bus(I2C_BUS)
				.device(ADS1115_ADDR)
				.build();

		try  {
			i2c2 = provider.create(config);
		} finally {
			pi4j.shutdown();
		}
		watchDogThread.start();
	}

	public double readVolts(int channel) throws InterruptedException {
		int raw = readSingleEnded(i2c2, channel);   // read AIN0
		double volts = rawToVolts(raw, 4.096); // adjust to match PGA below
		// Kick watchdog exactly as you already do
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
		System.out.println("Raw ADC = " + raw);
		System.out.printf("Voltage = %.6f V%n", volts);
		return volts;
	}
	/**
	 * Reads a single-ended channel 0..3 using single-shot mode.
	 */
	private static int readSingleEnded(I2C i2c, int channel) throws InterruptedException {
		if (channel < 0 || channel > 3) {
			throw new IllegalArgumentException("Channel must be 0..3");
		}

		/*
		 * Config register layout:
		 * bit 15    OS        = 1 (start single conversion)
		 * bits 14:12 MUX      = 100 + channel (AINx vs GND)
		 * bits 11:9 PGA       = 001 (±4.096V full scale)
		 * bit 8     MODE      = 1 (single-shot)
		 * bits 7:5  DR        = 100 (128 SPS)
		 * bits 4:0  COMP_*    = 00011 (disable comparator)
		 *
		 * Result for channel 0:
		 * 1 100 001 1 100 00011b
		 */
		int muxBits = switch (channel) {
		case 0 -> 0b100;
		case 1 -> 0b101;
		case 2 -> 0b110;
		case 3 -> 0b111;
		default -> throw new IllegalArgumentException("Channel must be 0..3");
		};

		int config =
				(1 << 15) |          // OS = start conversion
				(muxBits << 12) |    // MUX = AINx to GND
				(0b001 << 9) |       // PGA = ±4.096V
				(1 << 8) |           // MODE = single-shot
				(0b100 << 5) |       // DR = 128 SPS
				(0b11);              // COMP_QUE = disable comparator

		writeRegister16(i2c, REG_CONFIG, config);

		// At 128 SPS, one conversion is about 7.8 ms. Sleep a little longer.
		Thread.sleep(10);

		// Optional: poll OS bit until conversion completes
		while ((readRegister16(i2c, REG_CONFIG) & 0x8000) == 0) {
			Thread.sleep(1);
		}

		int raw = readRegister16(i2c, REG_CONVERSION);

		// Convert unsigned 16-bit container to signed Java int
		if ((raw & 0x8000) != 0) {
			raw -= 65536;
		}

		return raw;
	}

	/**
	 * Convert raw ADS1115 reading to volts.
	 * For a PGA of ±4.096V, LSB = 4.096 / 32768.
	 */
	private static double rawToVolts(int raw, double fullScaleVolts) {
		return raw * (fullScaleVolts / 32768.0);
	}

	/**
	 * Writes a 16-bit value MSB first, as required by the ADS1115.
	 */
	private static void writeRegister16(I2C i2c, int register, int value) {
		byte msb = (byte) ((value >> 8) & 0xFF);
		byte lsb = (byte) (value & 0xFF);
		i2c.writeRegister(register, new byte[]{msb, lsb}, 0, 2);
	}

	/**
	 * Reads a 16-bit register MSB first.
	 */
	private static int readRegister16(I2C i2c, int register) {
		byte[] buffer = new byte[2];
		i2c.write((byte) register); // set pointer register
		i2c.read(buffer, 0, 2);
		return ((buffer[0] & 0xFF) << 8) | (buffer[1] & 0xFF);
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
}