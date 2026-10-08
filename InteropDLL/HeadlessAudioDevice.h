#pragma once

#include "Core/Shared/Interfaces/IAudioDevice.h"

#include <algorithm>
#include <cstring>
#include <mutex>
#include <vector>

// A capture-only audio device for headless operation. Instead of playing
// audio to hardware, it buffers interleaved int16 PCM samples that can be
// read back via ReadSamples() (the audio analogue of SoftwareRenderer).
class HeadlessAudioDevice : public IAudioDevice
{
private:
	std::mutex _lock;
	std::vector<int16_t> _buffer;
	uint32_t _sampleRate = 48000;
	uint32_t _channelCount = 2;

public:
	void PlayBuffer(int16_t* soundBuffer, uint32_t bufferSize, uint32_t sampleRate, bool isStereo) override
	{
		uint32_t channelCount = isStereo ? 2 : 1;

		std::lock_guard<std::mutex> lock(_lock);
		_sampleRate = sampleRate;
		_channelCount = channelCount;

		size_t sampleCount = (size_t)bufferSize * channelCount;
		_buffer.insert(_buffer.end(), soundBuffer, soundBuffer + sampleCount);

		// Keep at most ~4 seconds of audio; drop the oldest samples otherwise.
		size_t maxSamples = (size_t)sampleRate * channelCount * 4;
		if(_buffer.size() > maxSamples) {
			_buffer.erase(_buffer.begin(), _buffer.begin() + (_buffer.size() - maxSamples));
		}
	}

	void Stop() override {}
	void Pause() override {}
	void ProcessEndOfFrame() override {}
	string GetAvailableDevices() override { return ""; }
	void SetAudioDevice(string deviceName) override {}
	AudioStatistics GetStatistics() override { return {}; }

	uint32_t GetSampleRate() const { return _sampleRate; }
	uint32_t GetChannelCount() const { return _channelCount; }

	// Copies up to maxSamples int16 values into `out` and removes them from
	// the buffer. Returns the number of samples actually copied.
	uint32_t ReadSamples(int16_t* out, uint32_t maxSamples)
	{
		std::lock_guard<std::mutex> lock(_lock);
		uint32_t count = (uint32_t)std::min<size_t>(_buffer.size(), maxSamples);
		if(count > 0) {
			memcpy(out, _buffer.data(), count * sizeof(int16_t));
			_buffer.erase(_buffer.begin(), _buffer.begin() + count);
		}
		return count;
	}
};
