#pragma once
#include <cstdint>
#include <string>
#include <vector>

namespace pm_torrent {
// C.19: the ONLY torrent-session interface used by MPV playback. Unlike the
// C.17 probe API it owns a real session and never starts per-read peer workers.
struct StreamingInfo {
  bool active = false;
  bool metadata = false;
  int selectedFile = -1;
  uint64_t selectedSize = 0;
  uint64_t verifiedAhead = 0;
  uint64_t verifiedTail = 0;
  int peers = 0;
  int seeds = 0;
  uint64_t downBytesPerSecond = 0;
  uint64_t demands = 0;
  int trackerCount = 0;
  int trackerReplies = 0;
  int trackerErrors = 0;
  int dhtHints = 0;
  int dhtNodes = 0;
  bool dhtBootstrapped = false;
  bool privateTorrent = false;
  std::string lastDiscoveryEvent;
  std::string lastError;
  std::string state;
};
struct StreamingOpenResult { bool ready = false; std::string status; };
bool StreamingEngineAvailable();
StreamingOpenResult OpenStreamingTorrent(const std::string& hash, int fileIndex,
    const std::string& trackers, const std::string& cacheDir);
StreamingInfo CurrentStreamingInfo();
// File-relative demand; queues coexist (opening, index, playback, seek).
void DemandStreamingRange(uint64_t offset, uint32_t length, int urgency = 0);
// Returns true ONLY when every requested selected-file byte has been verified.
// Missing data != end of stream; must never be returned as success.
bool ReadStreamingVerified(uint64_t offset, uint32_t length,
                           std::vector<uint8_t>& output);
uint64_t StreamingSelectedSize(const std::string& hash, int selectedFile);
std::string StreamingSelectedName(const std::string& hash, int selectedFile);
// Immediate cancellation signal: nonblocking and safe while metadata is being opened.
void RequestStreamingTorrentCancel();
void CloseStreamingTorrent();
// Detach the active session immediately. Its scheduler/network objects are
// destroyed by a background reaper so subsequent opens are never forced to
// wait for old libtorrent network shutdown after a user cancellation.
void CloseStreamingTorrentAsync();
}
