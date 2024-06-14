package com.example.sh_prototype


import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.media.MediaPlayer
import android.media.audiofx.AudioEffect
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.sh_prototype.databinding.ActivityPlayerBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase


class PlayerActivity : AppCompatActivity(), ServiceConnection, MediaPlayer.OnCompletionListener {

    private lateinit var databaseReference: DatabaseReference


    companion object {
        var nowPlayingId: String = ""
        lateinit var musicListPA: ArrayList<Music>
        var songPosition: Int = 0
        lateinit var binding: ActivityPlayerBinding
        var isPlaying: Boolean = false
        var musicService: MusicService? = null
        var repeat: Boolean = false
        var isFavorite: Boolean = false
        var fIndex: Int = -1
    }
    private val sleepStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val isAsleep = intent?.getBooleanExtra("IS_ASLEEP", false) ?: false
            if (isAsleep && isPlaying) {
                pauseMusic()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        registerReceiver(sleepStateReceiver, IntentFilter("SLEEP_STATE_UPDATE"),
            RECEIVER_NOT_EXPORTED)
        initializeLayout()
        initializeFirebase()
        setupButtonListeners()
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, this, BIND_AUTO_CREATE)
        binding.playPauseBtnPA.setOnClickListener {
            if (isPlaying) {
                pauseMusic()
            } else {
                playMusic()
            }
        }
        binding.backkBtnPA.setOnClickListener { finish()}

        binding.backBtnPA.setOnClickListener { prevNextSong(increment = false) }
        binding.nextBtnPA.setOnClickListener { prevNextSong(increment = true) }

        binding.seekBarPA.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) musicService?.mediaPlayer?.seekTo(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        binding.repeatBtnPA.setOnClickListener {
            if (!repeat) {
                repeat = true
                binding.repeatBtnPA.setColorFilter(ContextCompat.getColor(this, R.color.purple))
            } else {
                repeat = false
                binding.repeatBtnPA.setColorFilter(
                    ContextCompat.getColor(
                        this,
                        R.color.pink_salmon
                    )
                )
            }
        }
        binding.equalizerBtnPA.setOnClickListener {
            try {
                val EqIntent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                EqIntent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, musicService!!.mediaPlayer!!.audioSessionId)
                EqIntent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, baseContext.packageName)
                EqIntent.putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                startActivityForResult(EqIntent, 13)
            }catch (e: Exception){
                Toast.makeText(this, "Equalizer feature not supported", Toast.LENGTH_SHORT).show()}
        }
        binding.favoriteBtnPA.setOnClickListener {
            if(isFavorite){
                isFavorite = false
                binding.favoriteBtnPA.setImageResource(R.drawable.favorite_empty_icon)
                FavoriteActivity.favoriteSongs.removeAt(fIndex)
            }
            else{
                isFavorite = true
                binding.favoriteBtnPA.setImageResource(R.drawable.favorite_icon)
                FavoriteActivity.favoriteSongs.add(musicListPA[songPosition])
            }
        }
    }
    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        val binder = service as MusicService.MyBinder
        musicService = binder.currentService()
        musicService?.createMediaPlayer() // Call createMediaPlayer here
        musicService?.seekBarSetup()
    }


    override fun onServiceDisconnected(name: ComponentName?) {
        musicService = null
    }
    private fun initializeFirebase() {
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid
        if(currentUserUid != null){
            databaseReference = FirebaseDatabase.getInstance().getReference("Users").child(currentUserUid).child("DeviceStatus")
        }
    }

    private fun setLayout() {
        fIndex = favoriteChecker(musicListPA[songPosition].id)
        Glide.with(this)
            .load(musicListPA[songPosition].artUri)
            .apply(RequestOptions().placeholder(R.drawable.music).centerCrop())
            .into(binding.songImgPA)

        binding.songNamePA.text = musicListPA[songPosition].title
        if (repeat) binding.repeatBtnPA.setColorFilter(ContextCompat.getColor(this, R.color.purple))
        if(isFavorite) binding.favoriteBtnPA.setImageResource(R.drawable.favorite_icon)
        else binding.favoriteBtnPA.setImageResource(R.drawable.favorite_empty_icon)

    }

    private fun createMediaPlayer() {
        try {
            if (musicService!!.mediaPlayer == null) {
                musicService!!.mediaPlayer = MediaPlayer()
            }
            musicService!!.mediaPlayer!!.reset()

            val currentSong = musicListPA[songPosition]
            if (currentSong.id == "whitenoise") {
                // For white noise, we don't set the data source as we use create() for raw resources.
                musicService!!.mediaPlayer = MediaPlayer.create(this, R.raw.whitenoise)
            } else if (currentSong.id == "birds") {
                // For Birds preloaded song
                musicService!!.mediaPlayer = MediaPlayer.create(this, R.raw.birds)
            } else if (currentSong.id == "waves") {
                // For Waves preloaded song
                musicService!!.mediaPlayer = MediaPlayer.create(this, R.raw.waves)
            } else if (currentSong.id == "ocean") {
                // For Ocean preloaded song
                musicService!!.mediaPlayer = MediaPlayer.create(this, R.raw.ocean)
            } else if (currentSong.id == "rain") {
                // For Rain preloaded song
                musicService!!.mediaPlayer = MediaPlayer.create(this, R.raw.rain)
            } else {
                // For other songs, we set the data source to the file path.
                musicService!!.mediaPlayer!!.setDataSource(currentSong.path)
                musicService!!.mediaPlayer!!.prepare()
            }

            // Start the music player and update UI.
            musicService!!.mediaPlayer!!.start()
            isPlaying = true
            binding.playPauseBtnPA.setIconResource(R.drawable.pause)
            binding.tvSeekBarStart.text = formatDuration(musicService!!.mediaPlayer!!.currentPosition.toLong())
            binding.tvSeekBarEnd.text = formatDuration(musicService!!.mediaPlayer!!.duration.toLong())
            binding.seekBarPA.progress = 0
            binding.seekBarPA.max = musicService!!.mediaPlayer!!.duration
            musicService!!.mediaPlayer!!.setOnCompletionListener(this)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not play the song: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeLayout() {
        songPosition = intent.getIntExtra("index", 0)
        when (intent.getStringExtra("class")) {
            "FavoriteAdapter"->{
                val intent = Intent(this, MusicService::class.java)
                bindService(intent, this, BIND_AUTO_CREATE)
                startService(intent)
                musicListPA = ArrayList()
                musicListPA.addAll(FavoriteActivity.favoriteSongs)
                setLayout()
            }
            "NowPlaying" ->{
                setLayout()
                binding.tvSeekBarStart.text = formatDuration(musicService!!.mediaPlayer!!.currentPosition.toLong())
                binding.tvSeekBarEnd.text = formatDuration(musicService!!.mediaPlayer!!.duration.toLong())
                binding.seekBarPA.progress = musicService!!.mediaPlayer!!.currentPosition
                binding.seekBarPA.max = musicService!!.mediaPlayer!!.duration
            }
            "MusicAdapter" -> {
                val intent = Intent(this, MusicService::class.java)
                bindService(intent, this, BIND_AUTO_CREATE)
                startService(intent)
                musicListPA = ArrayList()
                musicListPA.addAll(Settings.MusicListMA)
                setLayout()
            }

            "Settings" -> {
                val intent = Intent(this, MusicService::class.java)
                bindService(intent, this, BIND_AUTO_CREATE)
                startService(intent)
                musicListPA = ArrayList()
                musicListPA.addAll(Settings.MusicListMA)
                musicListPA.shuffle()
                setLayout()
            }

            "FavoriteShuffle" ->{
                val intent = Intent(this, MusicService::class.java)
                bindService(intent, this, BIND_AUTO_CREATE)
                startService(intent)
                musicListPA = ArrayList()
                musicListPA.addAll(FavoriteActivity.favoriteSongs)
                musicListPA.shuffle()
                setLayout()
            }
            "PlaylistDetailsAdapter" ->{
                val intent = Intent(this, MusicService::class.java)
                bindService(intent, this, BIND_AUTO_CREATE)
                startService(intent)
                musicListPA = ArrayList()
                musicListPA.addAll(PlaylistActivity.musicPlaylist.ref[PlaylistDetails.currentPlaylistPos].playlist)
                setLayout()
            }
            "PlaylistDetailsShuffle" ->{
                val intent = Intent(this, MusicService::class.java)
                bindService(intent, this, BIND_AUTO_CREATE)
                startService(intent)
                musicListPA = ArrayList()
                musicListPA.addAll(PlaylistActivity.musicPlaylist.ref[PlaylistDetails.currentPlaylistPos].playlist)
                musicListPA.shuffle()
                setLayout()
            }
        }
    }

    private fun setupButtonListeners() {
        binding.playPauseBtnPA.setOnClickListener {
            if (isPlaying) pauseMusic()
            else playMusic()
        }
        // Additional listeners
    }

    private fun playMusic() {
        if (musicService?.mediaPlayer != null) {
            binding.playPauseBtnPA.setIconResource(R.drawable.pause)
            isPlaying = true
            musicService?.mediaPlayer?.start()
            updateSpeakerStatus(true)
        } else {
            Log.e("PlayerActivity", "Failed to play music - MediaPlayer is null")
        }
    }

    private fun pauseMusic() {
        if (musicService?.mediaPlayer != null) {
            binding.playPauseBtnPA.setIconResource(R.drawable.play)
            isPlaying = false
            musicService?.mediaPlayer?.pause()
            updateSpeakerStatus(false)
        } else {
            Log.e("PlayerActivity", "Failed to pause music - MediaPlayer is null")
        }
    }

    private fun prevNextSong(increment: Boolean) {
        if (increment) {
            setSongPosition(increment = true)
            setLayout()
            createMediaPlayer()
        } else {
            setSongPosition(increment = false)
            setLayout()
            createMediaPlayer()
        }
    }

    private fun setSongPosition(increment: Boolean) {
        if (increment) {
            if (musicListPA.size - 1 == songPosition)
                songPosition = 0
            else ++songPosition
        } else {
            if (0 == songPosition)
                songPosition = musicListPA.size - 1
            else --songPosition
        }
    }

    override fun onCompletion(mp: MediaPlayer?) {
        if (repeat) {
            // If repeat is enabled, play the current song again
            createMediaPlayer()
        } else {
            // If repeat is not enabled, proceed to the next song
            setSongPosition(increment = true)
            createMediaPlayer()
            try {
                setLayout()
            } catch (e: Exception) {
                return
            }
        }
    }
    private fun updateSpeakerStatus(isPlaying: Boolean) {
        val speakerStatus = if (isPlaying) 1 else 2
        val userUid = FirebaseAuth.getInstance().currentUser?.uid  // Replace with your logic to get the current user's UID

        if (userUid != null) {
            val ref = FirebaseDatabase.getInstance().getReference("Users/$userUid/DeviceStatus/Speakers")
            ref.setValue(speakerStatus)
                .addOnSuccessListener {
                }
                .addOnFailureListener {
                }
        } else {
            Log.e("PlayerActivity", "Failed to update speaker status - User UID is null")
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        // Unregister the BroadcastReceiver when the activity is destroyed
//        unregisterReceiver(sleepStateReceiver)
        unbindService(this)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 13 || resultCode == RESULT_OK)
            return
    }
}