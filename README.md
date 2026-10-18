# Etar Calendar
Etar (from Arabic:  `إِيتَار`)  is an open source with material 3 expressive designed calendar made for everyone! 
The mod version by Germanium changed all the traditional views to Jetpack Compose, which have more efficiency than view. 

## Why?
Well, I wanted a simple, material designed and state of the art open source calendar that anyone can make better. 
And thanks to Etar-Project so that I can accomplish my Mod version with my own thoughts.  

## Special thanks

The application is an enhanced version of AOSP Calendar. Without the help of
[Free Software for Android](https://github.com/Free-Software-for-Android/Standalone-Calendar) team, 
this app would be just a dream. So thanks to them!

## Germanium Mod Edition Features Ver 2.0
- Redesigned all traditional UI and UX with Jetpack Compose and Material 3 Expressive.
- New scrollable year view and three day view. 
- Redesigned widgets.
- Redesigned Day, Week and Month view. Now Month view scroll horizontally like Google Calendar. 
- Month view now have widgets.
- Date calcuator and high degree of Countdown customization.
- Countdown and event collaboration.
- Lunar calendar integrate.
- Redesigned agenda view and agenda adjunction.
- Redirectable of dates&months in views.
- ICS Offline/Online import supplement. 
- Refined new global animations.
- The mod`s full localization is available in Chinese (Simplifed/Traditional), English, Japanese and German. 
- Problem: Other Language`s localization is not ready in mod version, keeping vanilla language library.
- Renamed to Etar-Mod.
- Todo feature added, with interactable widgets.
- All in different Activities, seperated from AllInOneActivity. 
However AllInOneActivity have to be kept, since it is the entrance of many features.

## How to use Etar
Store your calendar on the phone only:
  - Create an offline calendar with the new calendar manager.

Sync your calendar to a server:
  - A cloud-synched calendar could be a google calendar, but you can also use
  any other public Caldav-server or even host your own (which would be the
  only way to keep full control over your data and still have ONE calendar
  usable from different devices.) To sync such a calendar to some server you
  need yet another app, e. g. DAVx5. That’s necessary because a Caldav client
  isn't included in Etar.

### Technical explanation
On Android there are "Calendar providers". These can be calendars that are
synchronized with a cloud service or local calendars. Basically any app
could provide a calendar. Those "provided" calendars can be used by Etar.
You can even configure in Etar which ones are to be shown and when adding
an event to which calendar it should be added.

### Important permissions Etar requires
- READ_CONTACTS(optional)  
  Is queried the first time an appointment is created and can be rejected. But then search and location suggestions no longer work.
->allows search and location suggestions when adding guests to an event  
- READ_CALENDAR & WRITE_CALENDAR  
->read and create calendar events


## Build instructions
Install and extract Android SDK command line tools.
```
tools/bin/sdkmanager platform-tools
export ANDROID_HOME=/path/to/android-sdk/
gradle :app:assembleDebug
```
## License

Copyright (c) 2005-2013, The Android Open Source Project

Copyright (c) 2013, Dominik Schürmann

Copyright (c) 2015-, The Etar Project

Copyright (c) 2026-, WinGermanium Mod

Licensed under the GPLv3: https://www.gnu.org/licenses/gpl-3.0.html
Except where otherwise noted.

Google Play and the Google Play logo are trademarks of Google Inc.
