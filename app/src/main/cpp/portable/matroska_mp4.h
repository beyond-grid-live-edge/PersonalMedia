#pragma once
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace pm_torrent {
// C.9 bounded preview remux of an already SHA-1 verified MKV prefix.
// Optional 45-second compatibility test; not a continuous streaming muxer.
// Does not decode, transcode, fetch missing pieces or support DRM.
struct MkvMp4Preview {
    bool ok = false;
    std::vector<uint8_t> mp4;
    uint32_t videoSamples = 0;
    uint32_t audioSamples = 0;
    uint32_t durationMs = 0;
    uint64_t sourceStartMs = 0;
    std::string diagnostic;
};
// segmentStartMs=0 preserves all verified C.9 previews.
// segmentStartMs=15000 or 30000 starts another ~15s test clip at the
// next HEVC keyframe. This is NOT a full MKV seek index or live muxer.
MkvMp4Preview RemuxVerifiedMkvPrefix(const uint8_t* bytes, size_t count,
                                   uint32_t previewMs = 3500, uint32_t segmentStartMs = 0,
                                   bool laterWindow = false, uint64_t absoluteTargetMs = 0);
}
