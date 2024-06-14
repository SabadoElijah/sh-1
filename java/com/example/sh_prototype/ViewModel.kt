package com.example.sh_prototype

import androidx.lifecycle.ViewModel

class AlarmViewModel : ViewModel() {
    var isAlarmSet: Boolean = false
    var alarmHour: Int = 0
    var alarmMinute: Int = 0
    var firebaseUpdated: Boolean = false
}
