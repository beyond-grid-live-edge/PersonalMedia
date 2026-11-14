#include "../app/src/main/cpp/portable/streaming_torrent_session.h"
#include <cassert>
#include <chrono>
#include <iostream>
using namespace pm_torrent;
int main() {
  // This host suite does not package libtorrent: exercises the immediate
  // cancellation + repeated-start fail-closed APIs. Real P2P teardown needs
  // the Android NDK/libtorrent device test.
  for (int i=0; i<40; ++i) {
    RequestStreamingTorrentCancel();
    CloseStreamingTorrentAsync();
    assert(!CurrentStreamingInfo().active);
    const auto result = OpenStreamingTorrent("0123456789012345678901234567890123456789", -1, "", "/tmp");
    assert(!result.ready);
  }
  CloseStreamingTorrent();
  assert(!CurrentStreamingInfo().active);
  std::cout << "STR5G cancellation/restart API, 40 repeated attempts: PASS\n";
}
