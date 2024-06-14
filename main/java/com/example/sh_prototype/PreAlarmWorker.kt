package com.example.sh_prototype

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class PreAlarmWorker(appContext: Context, workerParams: WorkerParameters):
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        updateDeviceStatusInFirebase()
        return Result.success()
    }

    private fun updateDeviceStatusInFirebase() {
        val userUid = FirebaseAuth.getInstance().currentUser?.uid
        if (userUid != null) {
            val databaseRef = FirebaseDatabase.getInstance().getReference("Users/$userUid/DeviceStatus")
            databaseRef.child("Light").setValue(4)
        }
    }
}
