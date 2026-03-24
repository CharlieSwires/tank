# runner.sh
#!/bin/bash
echo "===================================================running=========================================="
#export GST_DEBUG=3
sudo pkill pigpiod
sudo raspi-config nonint do_i2c 0
sudo ifconfig wlan0 down
sudo export WATCHDOG_ENABLED="true"
#~/submarine-control/libcam.sh &
WIDTH=640 HEIGHT=480 FPS=25 ~/tank/usb_mjpeg_rtsp.sh&
sudo -E java -jar ~/tank/target/tank.jar &

