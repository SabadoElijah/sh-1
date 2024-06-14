package com.example.sh_prototype

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST


interface SleepStateService {
    @POST("https://sleep-haven-fastapi.onrender.com/predict")
    fun getSleepState(@Body bpmData: BpmData): Call<SleepStateResponse>
}
