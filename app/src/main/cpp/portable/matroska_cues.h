#pragma once
#include <cstddef>
#include <cstdint>
#include <functional>
#include <string>
#include <vector>

namespace pm_torrent {
// Reads ONLY caller-supplied SHA-1-verified bytes of the selected video.
// Never initiates network traffic. In particular, a Cue pointing to a cluster
// does not imply those cluster bytes are cached or independently decodable.
using VerifiedMkvRead = std::function<bool(uint64_t, uint32_t, std::vector<uint8_t>&)>;
struct MkvIndexedSeek {
    bool indexed=false;                 // cue-indexed or local verified cluster mapped
    bool cueBased=false;
    bool cueTableCached=false;
    bool clusterHeaderCached=false;
    bool cueLocationKnown=false;
    uint64_t requestedMs=0;
    uint64_t indexedMs=0;
    uint64_t clusterByte=0;             // selected-video absolute byte offset
    uint64_t cueTableByte=0;
    uint32_t cueCount=0;
    uint32_t bufferPercent=0;           // selected Cluster, 0..99
    uint32_t cueBufferPercent=0;        // Cue-table location, 0..99
    uint64_t timestampScaleNs=1000000;
    std::string status;
};
MkvIndexedSeek ResolveVerifiedMkvSeek(const uint8_t* verifiedPrefix, size_t prefixLength,
    uint64_t sourceSize, uint64_t requestedMs, uint32_t localPercent,
    const VerifiedMkvRead& verifiedRead);
}
