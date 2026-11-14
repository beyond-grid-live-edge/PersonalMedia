#pragma once
#include <cstddef>
#include <cstdint>
#include <string>

namespace pm_torrent {
// The caller must supply a contiguous source-bound, SHA-1-verified opening
// prefix. This only reads Segment/Info; it is not a seek index or player.
struct MkvTimelineInfo {
    bool ready=false;
    uint32_t durationMs=0;
    uint64_t timestampScaleNs=1000000;
    std::string status;
};
MkvTimelineInfo ReadVerifiedMkvTimeline(const uint8_t* data,size_t len);
}
