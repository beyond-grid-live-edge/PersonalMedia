#pragma once
#include <cstdint>
#include <string>
namespace pm_torrent {
struct MpvRangeServerInfo {
  bool ready = false;
  std::string url;
  std::string status;
};
struct MpvRangeDemand {
  uint64_t offset = 0;
  uint64_t generation = 0;
  uint64_t requests = 0;
  uint64_t servedBytes = 0;
  uint64_t waiting = 0;
  uint64_t failures = 0;
  bool active = false;
};
// Binds only 127.0.0.1; the URL has an unguessable per-session token.
// Source and selected file must already be authenticated by native torrent metadata.
MpvRangeServerInfo StartMpvRangeServer(const std::string& hash, int fileIndex);
MpvRangeDemand CurrentMpvRangeDemand();
void StopMpvRangeServer();
}
