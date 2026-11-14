#include "../app/src/main/cpp/portable/torrent_media_type.h"
#include "../app/src/main/cpp/portable/streaming_torrent_session.h"
#include <cassert>
#include <iostream>
int main() {
  using namespace pm_torrent;
  // Explicit file index mismatch must not open another file's contents.
  assert(ResolveStreamingFileIndex(-1, 4) == 4);
  assert(ResolveStreamingFileIndex(4, 4) == 4);
  assert(ResolveStreamingFileIndex(3, 4) == -1);
  assert(ResolveStreamingFileIndex(-1, -1) == -1);
  // Streaming MIME comes from authenticated torrent metadata, never a URL.
  assert(TorrentMediaTypeFromName("Episodes/SHOW.MKV").contentType == "video/x-matroska");
  assert(TorrentMediaTypeFromName("Movie.mP4").extension == "mp4");
  assert(TorrentMediaTypeFromName("sample.webm").contentType == "video/webm");
  assert(TorrentMediaTypeFromName("broadcast.m2ts").extension == "ts");
  assert(TorrentMediaTypeFromName("unknown.exe").contentType == "application/octet-stream");
  assert(!StreamingEngineAvailable()); // fail closed with missing real libtorrent
  assert(StreamingSelectedName("0123456789012345678901234567890123456789", 0).empty());
  std::cout << "STR5 torrent metadata selection, typed media and fail-closed: PASS\n";
}
