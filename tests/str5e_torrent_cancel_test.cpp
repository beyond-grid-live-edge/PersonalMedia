#include "../app/src/main/cpp/portable/streaming_torrent_session.h"
#include <cassert>
#include <iostream>
int main(){
  using namespace pm_torrent;
  RequestStreamingTorrentCancel();
  assert(!CurrentStreamingInfo().active);
  auto result=OpenStreamingTorrent("0123456789012345678901234567890123456789",-1,"","/tmp");
  assert(!result.ready); // no libtorrent in host build: fail closed
  RequestStreamingTorrentCancel();
  CloseStreamingTorrent();
  assert(!CurrentStreamingInfo().active);
  std::cout << "STR-5E native cancellation API: PASS\n";
}
