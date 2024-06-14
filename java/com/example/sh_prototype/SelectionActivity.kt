package com.example.sh_prototype

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sh_prototype.databinding.ActivitySelectionBinding

class SelectionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySelectionBinding
    private lateinit var adapter: MusicAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.selectionRV.setItemViewCacheSize(10)
        binding.selectionRV.setHasFixedSize(true)
        binding.selectionRV.layoutManager = LinearLayoutManager(this)
        adapter = MusicAdapter(this,Settings.MusicListMA, selectionActivity = true)
        binding.selectionRV.adapter = adapter
        binding.backkBtnFA.setOnClickListener { finish() }
        binding.searchviewSA.setOnQueryTextListener(object : SearchView.OnQueryTextListener{
            override fun onQueryTextSubmit(query: String?): Boolean = true
            override fun onQueryTextChange(newText: String?): Boolean {
                Settings.musicListSearch = ArrayList()
                if(newText != null){
                    val userInput = newText.lowercase()
                    for (song in Settings.MusicListMA) {
                        if(song.title.lowercase().contains(userInput))
                            Settings.musicListSearch.add(song)
                    }
                    Settings.search = true
                    adapter.updateMusicList(searchList = Settings.musicListSearch)
                }
                return true
            }

    })
}
}