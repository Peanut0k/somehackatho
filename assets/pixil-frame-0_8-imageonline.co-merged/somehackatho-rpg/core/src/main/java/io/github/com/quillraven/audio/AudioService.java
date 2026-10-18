package io.github.com.peanut0k.audio;

import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.math.MathUtils;
import io.github.com.peanut0k.asset.AssetService;
import io.github.com.peanut0k.asset.MusicAsset;
import io.github.com.peanut0k.asset.SoundAsset;
import io.github.com.peanut0k.ldtk.LdtkMap;

public class AudioService {

    private final AssetService assetService;
    private Music currentMusic;
    private MusicAsset currentMusicAsset;
    private float musicVolume;
    private float soundVolume;

    public AudioService(AssetService assetService) {
        this.assetService = assetService;
        this.currentMusic = null;
        this.currentMusicAsset = null;
        this.musicVolume = 0.5f;
        this.soundVolume = 0.33f;
    }

    public void setMusicVolume(float volume) {
        this.musicVolume = MathUtils.clamp(volume, 0f, 1f);
        if (this.currentMusic != null) {
            this.currentMusic.setVolume(volume);
        }
    }

    public float getMusicVolume() {
        return musicVolume;
    }

    public void setSoundVolume(float soundVolume) {
        this.soundVolume = MathUtils.clamp(soundVolume, 0f, 1f);
    }

    public float getSoundVolume() {
        return soundVolume;
    }

    public void playMusic(MusicAsset musicAsset) {
        if (this.currentMusicAsset == musicAsset) {
            return;
        }

        if (this.currentMusic != null) {
            this.currentMusic.stop();
            this.assetService.unload(this.currentMusicAsset);
        }

        this.currentMusic = this.assetService.load(musicAsset);
        this.currentMusic.setLooping(true);
        this.currentMusic.setVolume(musicVolume);
        this.currentMusic.play();
        this.currentMusicAsset = musicAsset;
    }

    public void playSound(SoundAsset soundAsset) {
        this.assetService.get(soundAsset).play(this.soundVolume);
    }

    public void setMap(LdtkMap map) {
        String musicAssetStr = map.getMusic();
        MusicAsset musicAsset = musicAssetStr.isBlank() ? MusicAsset.TOWN : MusicAsset.valueOf(musicAssetStr);
        playMusic(musicAsset);
    }
}
