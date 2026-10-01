package com.karaoke.lounge.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karaoke.lounge.common.BizException;
import com.karaoke.lounge.common.Dict;
import com.karaoke.lounge.entity.Song;
import com.karaoke.lounge.repository.SongRepository;

/**
 * 歌曲库业务。
 *
 * <p>规则：① 歌曲编码唯一；② 语种须为合法枚举；③ 时长须 &gt; 0。</p>
 */
@Service
public class SongService {

    private final SongRepository songRepository;

    public SongService(SongRepository songRepository) {
        this.songRepository = songRepository;
    }

    @Transactional(readOnly = true)
    public List<Song> list(String language) {
        if (language != null && !language.isBlank()) {
            if (!Dict.isValidSongLanguage(language)) {
                throw new BizException("歌曲语种不合法：" + language);
            }
            return songRepository.findByLanguage(language);
        }
        return songRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Song get(Long id) {
        return songRepository.findById(id)
                .orElseThrow(() -> new BizException("歌曲不存在：" + id));
    }

    @Transactional
    public Song create(Song input) {
        if (input.getSongCode() == null || input.getSongCode().isBlank()) {
            throw new BizException("歌曲编码不能为空");
        }
        if (songRepository.existsBySongCode(input.getSongCode().trim())) {
            throw new BizException("歌曲编码已存在：" + input.getSongCode());
        }
        if (input.getSongName() == null || input.getSongName().isBlank()) {
            throw new BizException("歌曲名称不能为空");
        }
        if (input.getLanguage() != null && !input.getLanguage().isBlank()
                && !Dict.isValidSongLanguage(input.getLanguage())) {
            throw new BizException("歌曲语种不合法：" + input.getLanguage() + "，应为 " + Dict.SONG_LANGUAGES);
        }
        if (input.getDurationSec() != null && input.getDurationSec() <= 0) {
            throw new BizException("歌曲时长必须大于 0");
        }
        Song song = new Song();
        song.setSongCode(input.getSongCode().trim());
        song.setSongName(input.getSongName().trim());
        song.setSinger(input.getSinger() == null ? "" : input.getSinger().trim());
        song.setLanguage(input.getLanguage() == null ? "国语" : input.getLanguage());
        song.setDurationSec(input.getDurationSec());
        return songRepository.save(song);
    }
}
