#pragma once
#include <cstddef>
#include <cstdint>
#include <string>
#include <vector>

namespace pm_torrent {
// Diagnostic-only virtual MKV. Both inputs MUST be SHA-1-verified selected-file
// bytes obtained with ReadSourceRange by the caller. No network or AVPlayer.
struct MkvLaterWindow {
    bool ready = false;
    std::vector<uint8_t> virtualMkv;
    uint64_t firstClusterFileByte = 0;
    uint64_t firstClusterTimeMs = 0;
    uint64_t timecodeScaleNs = 1000000;
    uint32_t completeClusters = 0;
    uint32_t keyframeBlocks = 0;
    uint32_t audioBlocks = 0;
    std::string diagnostic;
};
MkvLaterWindow PrepareVerifiedMkvLaterWindow(const uint8_t* prefix,size_t prefixSize,
                              const uint8_t* later,size_t laterSize,uint64_t laterFileOffset);
}
