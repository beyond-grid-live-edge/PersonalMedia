#pragma once
#include <cstddef>
#include <cstdint>
namespace pm_torrent {
struct ClusterMarker {bool found=false; size_t offset=0; uint64_t timecodeTicks=0;};
// Seek-window scanner, entirely structural. Timecode is raw MKV ticks;
// converting to movie time requires Segment Info TimecodeScale and cue mapping.
ClusterMarker FindVerifiedMkvCluster(const uint8_t* data,size_t size);
}
