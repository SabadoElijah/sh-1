package com.example.sh_prototype

import java.util.concurrent.TimeUnit

data class Music(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long = 0,
    val path: String,
    val artUri: String
)

class Playlist{
    lateinit var name: String
    lateinit var playlist: ArrayList<Music>
    lateinit var createdOn: String
}
class MusicPlaylist{
    var ref: ArrayList<Playlist> = ArrayList()
}

fun formatDuration(duration: Long): String {
    val minutes = TimeUnit.MINUTES.convert(duration, TimeUnit.MILLISECONDS)
    val seconds = TimeUnit.SECONDS.convert(duration, TimeUnit.MILLISECONDS) - minutes * TimeUnit.SECONDS.convert(1, TimeUnit.MINUTES)
    return String.format("%2d:%2d", minutes, seconds)
}
fun setSongPosition(increment: Boolean){
    if(!PlayerActivity.repeat){
        if(increment)
        {
            if(PlayerActivity.musicListPA.size - 1 == PlayerActivity.songPosition)
                PlayerActivity.songPosition = 0
            else ++PlayerActivity.songPosition
        }else{
            if(0 == PlayerActivity.songPosition)
                PlayerActivity.songPosition = PlayerActivity.musicListPA.size-1
            else --PlayerActivity.songPosition
        }
    }
}
fun favoriteChecker(id: String): Int{
    PlayerActivity.isFavorite = false
    FavoriteActivity.favoriteSongs.forEachIndexed { index, music ->
        if(id == music.id){
            PlayerActivity.isFavorite = true
            return index
        }
    }
    return -1
}

