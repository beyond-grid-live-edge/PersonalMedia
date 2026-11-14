#include "../app/src/main/cpp/portable/native_live_tv.h"
#include "../app/src/main/cpp/portable/streaming_torrent_session.h"
#include "../app/src/main/cpp/portable/mpv_range_server.h"
#include <cassert>
#include <iostream>
int main() {
  assert(!pm_torrent::StreamingEngineAvailable());
  assert(!pm_torrent::OpenStreamingTorrent("invalid",0,"","/tmp").ready);
  assert(!pm_torrent::StartMpvRangeServer("invalid",0).ready);
  assert(!pm_live_tv::Connect("bad-host",5002,5001));
  assert(!pm_live_tv::StatusJson().empty());
  assert(!pm_live_tv::ChannelsJson().empty());
  pm_live_tv::Disconnect();
  std::cout << "Native fail-closed torrent, tuner input and status: PASS\n";
}
