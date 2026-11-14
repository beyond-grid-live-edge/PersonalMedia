#pragma once
#include <algorithm>
#include <cctype>
#include <string>

namespace pm_torrent {
struct TorrentMediaType {
  std::string extension = "bin";
  std::string contentType = "application/octet-stream";
};
// No untrusted filename reaches an HTTP response. Only constant values are returned.
inline TorrentMediaType TorrentMediaTypeFromName(std::string name) {
  const auto dot = name.find_last_of('.');
  if(dot == std::string::npos) return {};
  std::string ext = name.substr(dot+1);
  std::transform(ext.begin(), ext.end(), ext.begin(), [](unsigned char c){return char(std::tolower(c));});
  if(ext == "mkv") return {"mkv", "video/x-matroska"};
  if(ext == "mp4" || ext == "m4v") return {"mp4", "video/mp4"};
  if(ext == "webm") return {"webm", "video/webm"};
  if(ext == "avi") return {"avi", "video/x-msvideo"};
  if(ext == "ts" || ext == "m2ts") return {"ts", "video/mp2t"};
  if(ext == "mpg" || ext == "mpeg") return {"mpeg", "video/mpeg"};
  if(ext == "mov") return {"mov", "video/quicktime"};
  return {};
}
// A Stremio missing fileIdx (-1) means the engine must use its authenticated
// metadata selection, not a literal -1 in the Range server.
inline int ResolveStreamingFileIndex(int requested, int actual) {
  if(actual < 0) return -1;
  return requested == -1 || requested == actual ? actual : -1;
}
}
