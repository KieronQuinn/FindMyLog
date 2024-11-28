# Find my Log

Small Xposed module which listens for Find my Device updates, logs them and appends them to a CSV 
file. It works by hooking creation of LatLngs, filtering to location updates and automatically 
clicking the refresh button every 60 seconds.

This allows *very basic* location history creation for testing purposes. Please note that the 
purpose of this module is just that, it's not meant to be used day-to-day for location history 
tracking.

## Usage

- Build and install module
- Enable module in LSPosed & reboot
- Set developer option to "Keep screen on" to enabled so the device doesn't sleep. 
- Connect the device to power
- Open Find my Device, select the device you want to track
- The screen will go black, as the module adds a full screen black View in front of the UI to
prevent OLED burn in. This is normal.
- The module is now logging updates. You can check it's working by doing a `logcat`, filtered by the
tag `FML`
- When you're finished, exit out of full screen by swiping up on the navigation bar and closing the
app, this will stop logging.
- Pull the file `/data/data/com.google.android.apps.acm/files/locations.csv`. It contains locations
and timestamps. Please note that this CSV does not have headers, but the columns are:
  - Timestamp (ISO format, this is the time that the location arrived on the device, not necessarily 
the report time)
  - Latitude
  - Longitude