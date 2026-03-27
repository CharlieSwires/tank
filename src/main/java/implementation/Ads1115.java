package implementation;

import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.pi4j.Pi4J;
import com.pi4j.context.Context;
import com.pi4j.io.i2c.I2C;
import com.pi4j.io.i2c.I2CConfig;
import com.pi4j.io.i2c.I2CProvider;

import Const.Constant;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class Ads1115 {

    private static final Logger log = LoggerFactory.getLogger(Ads1115.class);

    private static final int REG_CONVERSION = 0x00;
    private static final int REG_CONFIG = 0x01;
    private static final int ADS1115_ADDR = 0x48;
    private static final int I2C_BUS = 1;

    private final Context pi4j;
    private final I2C i2c2;

    public WatchDog watchDogThread;
    public AtomicBoolean startTimer = new AtomicBoolean(false);
    private static boolean disabled = true;

    public Ads1115() {
        this.pi4j = Pi4J.newAutoContext();
        disabled = !"true".equals(System.getenv("WATCHDOG_ENABLED"));

        I2CProvider provider = pi4j.provider("linuxfs-i2c");
        I2CConfig config = I2C.newConfigBuilder(pi4j)
                .id("ADS1115")
                .name("ADS1115 ADC")
                .bus(I2C_BUS)
                .device(ADS1115_ADDR)
                .build();

        this.i2c2 = provider.create(config);

        watchDogThread = new WatchDog();
        watchDogThread.start();
    }

    public synchronized double readVolts(int channel) {
        try {
            int raw = readSingleEnded(channel);
            double volts = rawToVolts(raw, 4.096);

            if (watchDogThread != null) {
                startTimer.set(!disabled);
                watchDogThread.interrupt();
            }

            log.info("Raw ADC = {}", raw);
            log.info("Voltage = {}", volts);
            return volts;
        } catch (Exception e) {
            log.error("Problem getting volts", e);
            return Double.parseDouble("" + Constant.ERROR);
        }
    }

    private int readSingleEnded(int channel) throws InterruptedException {
        if (channel < 0 || channel > 3) {
            throw new IllegalArgumentException("Channel must be 0..3");
        }

        int muxBits = switch (channel) {
            case 0 -> 0b100;
            case 1 -> 0b101;
            case 2 -> 0b110;
            case 3 -> 0b111;
            default -> throw new IllegalArgumentException("Channel must be 0..3");
        };

        int config =
                (1 << 15) |
                (muxBits << 12) |
                (0b001 << 9) |
                (1 << 8) |
                (0b100 << 5) |
                (0b11);

        writeRegister16(REG_CONFIG, config);

        Thread.sleep(10);

        while ((readRegister16(REG_CONFIG) & 0x8000) == 0) {
            Thread.sleep(1);
        }

        int raw = readRegister16(REG_CONVERSION);

        if ((raw & 0x8000) != 0) {
            raw -= 65536;
        }

        return raw;
    }

    private static double rawToVolts(int raw, double fullScaleVolts) {
        return raw * (fullScaleVolts / 32768.0);
    }

    private void writeRegister16(int register, int value) {
        byte msb = (byte) ((value >> 8) & 0xFF);
        byte lsb = (byte) (value & 0xFF);
        i2c2.writeRegister(register, new byte[] { msb, lsb }, 0, 2);
    }

    private int readRegister16(int register) {
        byte[] buffer = new byte[2];
        i2c2.write((byte) register);
        i2c2.read(buffer, 0, 2);
        return ((buffer[0] & 0xFF) << 8) | (buffer[1] & 0xFF);
    }

    @PreDestroy
    public void shutdown() {
        try {
            pi4j.shutdown();
        } catch (Exception e) {
            log.warn("Error shutting down Pi4J", e);
        }
    }

    public class WatchDog extends Thread {
        private AtomicBoolean timeout = new AtomicBoolean(false);

        @Override
        public void run() {
            while (true) {
                if (startTimer.get()) {
                    try {
                        WatchDog.sleep(10000);
                        setTimeout(true);
                        log.error("Watchdog timed out!!");
                    } catch (InterruptedException e) {
                        setTimeout(false);
                    }
                } else {
                    setTimeout(false);
                    try {
                        WatchDog.sleep(500);
                    } catch (InterruptedException e) {
                    }
                }
            }
        }

        private void setTimeout(boolean b) {
            timeout.set(b);
        }

        public boolean getTimeout() {
            return timeout.get();
        }
    }
}