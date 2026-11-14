#pragma once
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace pm_torrent {
// C.6: bounded Matroska structural demux probe. No decoding, remux or AVPlayer.
// Caller supplies only the SHA-1-verified, selected-file prefix.
struct MatroskaTrack {
    uint64_t number = 0;
    uint64_t type = 0; // EBML: 1=video, 2=audio, 17=subtitle
    uint64_t width = 0, height = 0, channels = 0;
    bool codecPrivate = false;
    bool encrypted = false;
    std::string codec;
};
struct MatroskaPacket {
    bool found = false;
    bool keyframe = false;
    uint64_t track = 0;
    int16_t relativeTimecode = 0;
    uint64_t offset = 0; // selected-video-file absolute byte offset
    uint32_t size = 0;
};
struct MatroskaProbe {
    bool isMatroska = false;
    bool tracksFound = false;
    std::vector<MatroskaTrack> tracks;
    MatroskaPacket firstVideoPacket;
    std::string issue;
};
MatroskaProbe ProbeMatroskaPrefix(const uint8_t* data, size_t len);
std::string DescribeMatroska(const MatroskaProbe& probe);
}
