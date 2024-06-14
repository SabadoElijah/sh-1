package com.example.sh_prototype

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.*
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sh_prototype.databinding.FragmentSettingsBinding
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File


class Settings() : Fragment() {

    private lateinit var binding: FragmentSettingsBinding
    private lateinit var musicAdapter: MusicAdapter
    private val coroutineScope = CoroutineScope(Dispatchers.Main)



    companion object {
        var search: Boolean = false
        lateinit var MusicListMA: ArrayList<Music>
        lateinit var musicListSearch: ArrayList<Music>
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentSettingsBinding.inflate(inflater, container, false)
        val view = binding.root

        view.findViewById<Button>(R.id.light_button).setOnClickListener {
            navigateToLightFragment()
        }

        view.findViewById<Button>(R.id.scent_button).setOnClickListener {
            navigateToScentFragment()
        }

        binding.musicButton.setOnClickListener {
            // Navigate to the default view fragment
            navigateToDefaultView()
        }

        return view
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        coroutineScope.launch {
            if (requestRuntimePermission()) {
                initializeLayout(requireActivity() as AppCompatActivity, binding)
                loadFavoritesAndPlaylist()
            }
        }

        if (requestRuntimePermission()) {
            initializeLayout(requireActivity() as AppCompatActivity, binding)
            FavoriteActivity.favoriteSongs = ArrayList()
            val editor = getSharedPreferences("FAVORITES", AppCompatActivity.MODE_PRIVATE)
            val jsonString = editor.getString("FavoriteSongs", null)
            val typeToken = object : TypeToken<ArrayList<Music>>() {}.type
            if (jsonString != null) {
                val data: ArrayList<Music> = GsonBuilder().create().fromJson(jsonString, typeToken)
                FavoriteActivity.favoriteSongs.addAll(data)
            }
            PlaylistActivity.musicPlaylist = MusicPlaylist()
            val jsonStringPlaylist = editor.getString("MusicPlaylist", null)
            if (jsonStringPlaylist != null) {
                val dataPlaylist: MusicPlaylist =
                    GsonBuilder().create().fromJson(jsonStringPlaylist, MusicPlaylist::class.java)
                PlaylistActivity.musicPlaylist = dataPlaylist
            }
        }
        binding.shuffle.setOnClickListener {
            val intent = Intent(requireContext(), PlayerActivity::class.java)
            intent.putExtra("index", 0)
            intent.putExtra("class", "Settings")
            startActivity(intent)
        }
        binding.favorites.setOnClickListener {
            val intent = Intent(requireContext(), FavoriteActivity::class.java)
            startActivity(intent)
        }
        binding.playlist.setOnClickListener {
            val intent = Intent(requireContext(), PlaylistActivity::class.java)
            startActivity(intent)

        }

        val musicList = getAllAudio()
        MusicListMA = musicList
        musicAdapter = MusicAdapter(requireContext(), musicList)
        binding.musicRV.apply {
            setHasFixedSize(true)
            setItemViewCacheSize(13)
            layoutManager = LinearLayoutManager(requireContext())
            adapter = musicAdapter
        }
        binding.totalsongs.text = "Total Songs: ${musicAdapter.itemCount}"

        // Preload the whitenoise music
        preloadMusic(requireContext())
    }

    private suspend fun loadFavoritesAndPlaylist() = withContext(Dispatchers.IO) {
        // Long running operations such as loading from database or sharedPreferences
        val musicList = getAllAudio()
        withContext(Dispatchers.Main) {
            // Update UI after loading data
            MusicListMA = musicList
            musicAdapter = MusicAdapter(requireContext(), musicList)
            binding.musicRV.apply {
                setHasFixedSize(true)
                setItemViewCacheSize(13)
                layoutManager = LinearLayoutManager(requireContext())
                adapter = musicAdapter
            }
            binding.totalsongs.text = "Total Songs: ${musicAdapter.itemCount}"
        }
    }

    private fun navigateToLightFragment() {
        // Create an instance of LightFragment
        val lightFragment = LightFragment()

        // Replace the current fragment with LightFragment
        parentFragmentManager.beginTransaction()
            .replace(R.id.frameLayoutMusic, lightFragment)
            .addToBackStack(null)  // Optional: This allows users to navigate back
            .commit()
    }

    private fun navigateToScentFragment() {
        // Create an instance of ScentFragment
        val scentFragment = ScentFragment()

        // Replace the current fragment with ScentFragment
        parentFragmentManager.beginTransaction()
            .replace(R.id.frameLayoutMusic, scentFragment)
            .addToBackStack(null)  // Optional: This allows users to navigate back
            .commit()
    }

    private fun navigateToDefaultView() {
        // Replace the current fragment in the frameLayoutMusic with the default view fragment
        parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    private fun requestRuntimePermission(): Boolean {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                13
            )
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 13) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(requireContext(), "Permission Granted", Toast.LENGTH_SHORT)
                    .show()
            } else {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    13
                )
            }
        }
    }

    private fun initializeLayout(
        activity: AppCompatActivity,
        binding: FragmentSettingsBinding
    ) {
        val toggle = ActionBarDrawerToggle(
            activity,
            binding.root,
            R.string.opendrawer,
            R.string.closedrawer
        )
        binding.root.addDrawerListener(toggle)
        toggle.syncState()
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    @SuppressLint("Range")
    private fun getAllAudio(): ArrayList<Music> {
        val tempList = ArrayList<Music>()
        val selection = MediaStore.Audio.Media.IS_MUSIC + " !=0"
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
        )
        val cursor = requireContext().contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null, MediaStore.Audio.Media.DATE_ADDED + " DESC", null
        )
        if (cursor != null && cursor.moveToFirst()) {
            do {
                val idC = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media._ID))
                val titleC =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE))
                val albumC =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM))
                val artistC =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST))
                val pathC = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA))
                val albumIdC =
                    cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID))
                        .toString()
                val uri = Uri.parse("content://media/external/audio/albumart")
                val artUriC = Uri.withAppendedPath(uri, albumIdC).toString()
                val durationC =
                    cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION))
                val music =
                    Music(idC, titleC, albumC, artistC, durationC, pathC, artUri = artUriC)
                val file = File(music.path)
                if (file.exists()) {
                    tempList.add(music)
                }
            } while (cursor.moveToNext())
        }
        cursor?.close()

        return tempList
    }
    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel() // Cancel coroutines when the fragment is destroyed
    }

    override fun onResume() {
        super.onResume()
        val editor = getSharedPreferences("FAVORITES", AppCompatActivity.MODE_PRIVATE).edit()
        val jsonString = GsonBuilder().create().toJson(FavoriteActivity.favoriteSongs)
        editor.putString("FavouriteSongs", jsonString)
        val jsonStringPlaylist = GsonBuilder().create().toJson(PlaylistActivity.musicPlaylist)
        editor.putString("MusicPlaylist", jsonStringPlaylist)
        editor.apply()
    }

    private fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        return requireActivity().getSharedPreferences(name, mode)
    }

    private fun preloadMusic(context: Context) {
        // Preload Birds
        val birdsResourceId = R.raw.birds
        val birdsMediaPlayer = MediaPlayer.create(context, birdsResourceId)
        val birdsDuration = birdsMediaPlayer.duration.toLong()
//        birdsMediaPlayer.release()
        val birdsMusic = Music(
            id = "birds",
            title = "Birds",
            album = "Birds Album",
            artist = "Various Artists",
            duration = birdsDuration,
            path = "",
            artUri = ""
        )
        MusicListMA.add(birdsMusic)

        // Preload Waves
        val wavesResourceId = R.raw.waves
        val wavesMediaPlayer = MediaPlayer.create(context, wavesResourceId)
        val wavesDuration = wavesMediaPlayer.duration.toLong()
//        wavesMediaPlayer.release()
        val wavesMusic = Music(
            id = "waves",
            title = "Waves",
            album = "Waves Album",
            artist = "Various Artists",
            duration = wavesDuration,
            path = "",
            artUri = ""
        )
        MusicListMA.add(wavesMusic)

        // Preload Ocean
        val oceanResourceId = R.raw.ocean
        val oceanMediaPlayer = MediaPlayer.create(context, oceanResourceId)
        val oceanDuration = oceanMediaPlayer.duration.toLong()
//        oceanMediaPlayer.release()
        val oceanMusic = Music(
            id = "ocean",
            title = "Ocean",
            album = "Ocean Album",
            artist = "Various Artists",
            duration = oceanDuration,
            path = "",
            artUri = ""
        )
        MusicListMA.add(oceanMusic)

        // Preload Rain
        val rainResourceId = R.raw.rain
        val rainMediaPlayer = MediaPlayer.create(context, rainResourceId)
        val rainDuration = rainMediaPlayer.duration.toLong()
//        rainMediaPlayer.release()
        val rainMusic = Music(
            id = "rain",
            title = "Rain",
            album = "Rain Album",
            artist = "Various Artists",
            duration = rainDuration,
            path = "",
            artUri = ""
        )
        MusicListMA.add(rainMusic)

        // Preload White Noise
        val whiteNoiseResourceId = R.raw.whitenoise
        val whiteNoiseMediaPlayer = MediaPlayer.create(context, whiteNoiseResourceId)
        val whiteNoiseDuration = whiteNoiseMediaPlayer.duration.toLong()
//        whiteNoiseMediaPlayer.release()
        val whiteNoiseMusic = Music(
            id = "whitenoise",
            title = "White Noise",
            album = "White Noise Album",
            artist = "Various Artists",
            duration = whiteNoiseDuration,
            path = "",
            artUri = ""
        )
        MusicListMA.add(whiteNoiseMusic)

        // Notify the adapter that data has changed
        musicAdapter.notifyDataSetChanged()
    }


}



