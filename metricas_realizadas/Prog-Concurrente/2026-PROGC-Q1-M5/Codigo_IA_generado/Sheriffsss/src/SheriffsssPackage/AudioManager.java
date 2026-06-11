package SheriffsssPackage;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;

public class AudioManager {
	private static final int MAX_OVERLAPPING_SFX_CLIPS = 12;

	private final Map<String, Clip> clipCache = new HashMap<String, Clip>();
	private final Map<String, ArrayList<Clip>> oneShotClipCache = new HashMap<String, ArrayList<Clip>>();
	private final Map<Clip, LineListener> completionListeners = new IdentityHashMap<Clip, LineListener>();
	private Clip loopingClip;
	private String loopingResourcePath;
	private float loopingBaseGainDb;
	private Clip sfxLoopingClip;
	private String sfxLoopingResourcePath;
	private float sfxLoopingBaseGainDb;
	private final Map<String, Clip> keyedSfxLoopingClips = new HashMap<String, Clip>();
	private final Map<String, String> keyedSfxLoopingResourcePaths = new HashMap<String, String>();
	private final Map<String, Float> keyedSfxLoopingBaseGainDb = new HashMap<String, Float>();
	private final Map<String, Double> keyedSfxLoopingVolumeScales = new HashMap<String, Double>();
	private double musicVolume = 0.75;
	private double sfxVolume = 0.75;
	private boolean closed;

	public synchronized void playLoop(String resourcePath, float gainDb) {
		if (this.closed) {
			return;
		}
		Clip clip = getClip(resourcePath);
		removeCompletionListener(clip);
		if (this.loopingClip == clip) {
			this.loopingResourcePath = resourcePath;
			this.loopingBaseGainDb = gainDb;
			configureGain(clip, effectiveGain(gainDb, this.musicVolume));
			if (!clip.isRunning()) {
				clip.setFramePosition(0);
				clip.loop(Clip.LOOP_CONTINUOUSLY);
				clip.start();
			}
			return;
		}
		if (this.loopingClip != null && this.loopingClip != clip) {
			stopLoop();
		}
		this.loopingResourcePath = resourcePath;
		this.loopingBaseGainDb = gainDb;
		configureGain(clip, effectiveGain(gainDb, this.musicVolume));
		clip.stop();
		clip.setFramePosition(0);
		clip.loop(Clip.LOOP_CONTINUOUSLY);
		clip.start();
		this.loopingClip = clip;
	}

	public synchronized void playOnce(String resourcePath, float gainDb) {
		playOnce(resourcePath, gainDb, 1.0);
	}

	public synchronized void playOnce(String resourcePath, float gainDb, double volumeScale) {
		if (this.closed) {
			return;
		}
		if (volumeScale <= 0.0) {
			return;
		}
		Clip clip = getOneShotClip(resourcePath);
		configureGain(clip, effectiveGain(gainDb, this.sfxVolume * clamp01(volumeScale)));
		clip.setFramePosition(0);
		clip.start();
	}

	public synchronized void playOnceUntilFinished(String resourcePath, float gainDb, double volumeScale) {
		if (this.closed) {
			return;
		}
		if (volumeScale <= 0.0) {
			return;
		}
		Clip clip = loadClip(resourcePath);
		configureGain(clip, effectiveGain(gainDb, this.sfxVolume * clamp01(volumeScale)));
		LineListener listener = new LineListener() {
			@Override
			public void update(LineEvent event) {
				if (event.getType() == LineEvent.Type.STOP || event.getType() == LineEvent.Type.CLOSE) {
					synchronized (AudioManager.this) {
						clip.removeLineListener(this);
						clip.flush();
						clip.close();
					}
				}
			}
		};
		clip.addLineListener(listener);
		clip.setFramePosition(0);
		clip.start();
	}

	public synchronized void playSfxLoop(String resourcePath, float gainDb) {
		playSfxLoop(resourcePath, gainDb, 1.0);
	}

	public synchronized void playSfxLoop(String resourcePath, float gainDb, double volumeScale) {
		if (this.closed) {
			return;
		}
		if (volumeScale <= 0.0) {
			stopSfxLoop();
			return;
		}
		Clip clip = getClip(resourcePath);
		removeCompletionListener(clip);
		if (this.sfxLoopingClip == clip) {
			this.sfxLoopingResourcePath = resourcePath;
			this.sfxLoopingBaseGainDb = gainDb;
			configureGain(clip, effectiveGain(gainDb, this.sfxVolume * clamp01(volumeScale)));
			if (!clip.isRunning()) {
				clip.setFramePosition(0);
				clip.loop(Clip.LOOP_CONTINUOUSLY);
				clip.start();
			}
			return;
		}
		if (this.sfxLoopingClip != null && this.sfxLoopingClip != clip) {
			stopSfxLoop();
		}
		this.sfxLoopingResourcePath = resourcePath;
		this.sfxLoopingBaseGainDb = gainDb;
		configureGain(clip, effectiveGain(gainDb, this.sfxVolume * clamp01(volumeScale)));
		clip.stop();
		clip.setFramePosition(0);
		clip.loop(Clip.LOOP_CONTINUOUSLY);
		clip.start();
		this.sfxLoopingClip = clip;
	}

	public synchronized void playSfxLoop(String key, String resourcePath, float gainDb, double volumeScale) {
		if (this.closed || key == null || key.isEmpty()) {
			return;
		}
		if (volumeScale <= 0.0) {
			stopSfxLoop(key);
			return;
		}
		Clip clip = getKeyedSfxLoopClip(key, resourcePath);
		removeCompletionListener(clip);
		this.keyedSfxLoopingResourcePaths.put(key, resourcePath);
		this.keyedSfxLoopingBaseGainDb.put(key, Float.valueOf(gainDb));
		this.keyedSfxLoopingVolumeScales.put(key, Double.valueOf(clamp01(volumeScale)));
		configureGain(clip, effectiveGain(gainDb, this.sfxVolume * clamp01(volumeScale)));
		if (!clip.isRunning()) {
			clip.setFramePosition(0);
			clip.loop(Clip.LOOP_CONTINUOUSLY);
			clip.start();
		}
	}

	public synchronized void stopLoop() {
		if (this.loopingClip != null) {
			this.loopingClip.stop();
			this.loopingClip.setFramePosition(0);
			this.loopingClip = null;
			this.loopingResourcePath = null;
		}
	}

	public synchronized void stopSfxLoop() {
		if (this.sfxLoopingClip != null) {
			this.sfxLoopingClip.stop();
			this.sfxLoopingClip.setFramePosition(0);
			this.sfxLoopingClip = null;
			this.sfxLoopingResourcePath = null;
		}
	}

	public synchronized void stopSfxLoop(String key) {
		Clip clip = this.keyedSfxLoopingClips.remove(key);
		this.keyedSfxLoopingResourcePaths.remove(key);
		this.keyedSfxLoopingBaseGainDb.remove(key);
		this.keyedSfxLoopingVolumeScales.remove(key);
		if (clip != null) {
			removeCompletionListener(clip);
			clip.stop();
			clip.setFramePosition(0);
			clip.flush();
			clip.close();
		}
	}

	public synchronized void setMusicVolume(double volume) {
		this.musicVolume = clamp01(volume);
		if (this.loopingClip != null) {
			configureGain(this.loopingClip, effectiveGain(this.loopingBaseGainDb, this.musicVolume));
		}
	}

	public synchronized void setSfxVolume(double volume) {
		this.sfxVolume = clamp01(volume);
		if (this.sfxLoopingClip != null) {
			configureGain(this.sfxLoopingClip, effectiveGain(this.sfxLoopingBaseGainDb, this.sfxVolume));
		}
		for (Map.Entry<String, Clip> entry : this.keyedSfxLoopingClips.entrySet()) {
			Float baseGain = this.keyedSfxLoopingBaseGainDb.get(entry.getKey());
			Double volumeScale = this.keyedSfxLoopingVolumeScales.get(entry.getKey());
			if (baseGain != null && volumeScale != null) {
				configureGain(entry.getValue(), effectiveGain(baseGain.floatValue(), this.sfxVolume * volumeScale.doubleValue()));
			}
		}
	}

	public synchronized double getMusicVolume() {
		return this.musicVolume;
	}

	public synchronized double getSfxVolume() {
		return this.sfxVolume;
	}

	public synchronized void shutdown() {
		if (this.closed) {
			return;
		}
		this.closed = true;
		this.loopingClip = null;
		this.loopingResourcePath = null;
		this.sfxLoopingClip = null;
		this.sfxLoopingResourcePath = null;
		for (Clip clip : this.keyedSfxLoopingClips.values()) {
			clip.stop();
			clip.flush();
			clip.close();
		}
		this.keyedSfxLoopingClips.clear();
		this.keyedSfxLoopingResourcePaths.clear();
		this.keyedSfxLoopingBaseGainDb.clear();
		this.keyedSfxLoopingVolumeScales.clear();
		for (Map.Entry<Clip, LineListener> entry : this.completionListeners.entrySet()) {
			entry.getKey().removeLineListener(entry.getValue());
		}
		this.completionListeners.clear();
		for (Clip clip : this.clipCache.values()) {
			clip.stop();
			clip.flush();
			clip.close();
		}
		this.clipCache.clear();
		for (ArrayList<Clip> clips : this.oneShotClipCache.values()) {
			for (Clip clip : clips) {
				clip.stop();
				clip.flush();
				clip.close();
			}
		}
		this.oneShotClipCache.clear();
	}

	private Clip getClip(String resourcePath) {
		Clip clip = this.clipCache.get(resourcePath);
		if (clip != null) {
			return clip;
		}
		Clip loadedClip = loadClip(resourcePath);
		this.clipCache.put(resourcePath, loadedClip);
		return loadedClip;
	}

	private Clip getOneShotClip(String resourcePath) {
		ArrayList<Clip> clips = this.oneShotClipCache.get(resourcePath);
		if (clips == null) {
			clips = new ArrayList<Clip>();
			this.oneShotClipCache.put(resourcePath, clips);
		}
		for (int i = 0; i < clips.size(); i++) {
			Clip clip = clips.get(i);
			if (!clip.isRunning()) {
				clip.stop();
				clip.setFramePosition(0);
				return clip;
			}
		}
		if (clips.size() < MAX_OVERLAPPING_SFX_CLIPS) {
			Clip loadedClip = loadClip(resourcePath);
			clips.add(loadedClip);
			return loadedClip;
		}
		Clip oldestClip = clips.get(0);
		oldestClip.stop();
		oldestClip.setFramePosition(0);
		return oldestClip;
	}

	private Clip getKeyedSfxLoopClip(String key, String resourcePath) {
		Clip clip = this.keyedSfxLoopingClips.get(key);
		String currentResourcePath = this.keyedSfxLoopingResourcePaths.get(key);
		if (clip != null && resourcePath.equals(currentResourcePath)) {
			return clip;
		}
		if (clip != null) {
			clip.stop();
			clip.flush();
			clip.close();
		}
		Clip loadedClip = loadClip(resourcePath);
		this.keyedSfxLoopingClips.put(key, loadedClip);
		return loadedClip;
	}

	private void removeCompletionListener(Clip clip) {
		LineListener listener = this.completionListeners.remove(clip);
		if (listener != null) {
			clip.removeLineListener(listener);
		}
	}

	private Clip loadClip(String resourcePath) {
		try {
			return loadClipResource(resourcePath);
		} catch (Exception e) {
			throw new IllegalStateException("Unable to load audio resource: " + resourcePath, e);
		}
	}

	private Clip loadClipResource(String resourcePath) throws Exception {
		URL resource = getClass().getClassLoader().getResource(resourcePath);
		if (resource == null) {
			throw new IllegalStateException("Missing audio resource: " + resourcePath);
		}
		try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(resource)) {
			AudioInputStream playableStream = toPlayableStream(audioInputStream);
			try {
				Clip clip = AudioSystem.getClip();
				clip.open(playableStream);
				return clip;
			} finally {
				if (playableStream != audioInputStream) {
					playableStream.close();
				}
			}
		}
	}

	private AudioInputStream toPlayableStream(AudioInputStream audioInputStream) {
		AudioFormat format = audioInputStream.getFormat();
		if (format.getEncoding() == AudioFormat.Encoding.PCM_SIGNED) {
			return audioInputStream;
		}
		AudioFormat decodedFormat = new AudioFormat(
			AudioFormat.Encoding.PCM_SIGNED,
			format.getSampleRate(),
			16,
			format.getChannels(),
			format.getChannels() * 2,
			format.getSampleRate(),
			false
		);
		return AudioSystem.getAudioInputStream(decodedFormat, audioInputStream);
	}

	private void configureGain(Clip clip, float gainDb) {
		if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
			FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
			control.setValue(Math.max(control.getMinimum(), Math.min(control.getMaximum(), gainDb)));
		}
	}

	private float effectiveGain(float baseGainDb, double volume) {
		if (volume <= 0.0) {
			return -80f;
		}
		return baseGainDb + (float) (20.0 * Math.log10(volume));
	}

	private double clamp01(double value) {
		return Math.max(0.0, Math.min(1.0, value));
	}
}
